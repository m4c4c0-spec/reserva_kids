package cl.reservakids.application;

import cl.reservakids.application.dto.AdminDtos.AdminResumen;
import cl.reservakids.application.dto.AdminDtos.CrearAdminRequest;
import cl.reservakids.application.dto.AdminDtos.MetricasGlobales;
import cl.reservakids.application.dto.AdminDtos.NegocioAdminResumen;
import cl.reservakids.application.usecase.AdminService;
import cl.reservakids.domain.model.AdminAuditLog;
import cl.reservakids.domain.model.Administrador;
import cl.reservakids.domain.model.EstadoReserva;
import cl.reservakids.domain.model.Tenant;
import cl.reservakids.domain.repository.AdminAuditLogRepository;
import cl.reservakids.domain.repository.AdministradorRepository;
import cl.reservakids.domain.repository.CuentaClienteRepository;
import cl.reservakids.domain.repository.PagoRepository;
import cl.reservakids.domain.repository.RefreshTokenRepository;
import cl.reservakids.domain.repository.ReservaRepository;
import cl.reservakids.domain.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock TenantRepository tenantRepository;
    @Mock ReservaRepository reservaRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock CuentaClienteRepository cuentaClienteRepository;
    @Mock PagoRepository pagoRepository;
    @Mock AdministradorRepository administradorRepository;
    @Mock AdminAuditLogRepository auditLogRepository;
    @Mock org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @InjectMocks AdminService service;

    @Test
    void suspenderMueveEstadoYRevocaSesionesDelTenant() {
        Tenant t = tenantConEstado(7L, Tenant.ESTADO_ACTIVO);
        when(tenantRepository.findById(7L)).thenReturn(Optional.of(t));
        when(reservaRepository.countByTenantId(7L)).thenReturn(3L);

        NegocioAdminResumen resp = service.suspender(7L, 1L);

        assertEquals(Tenant.ESTADO_SUSPENDIDO, t.getEstado());
        assertEquals(Tenant.ESTADO_SUSPENDIDO, resp.estado());
        assertEquals(3L, resp.reservas());
        verify(refreshTokenRepository).revocarTodosDeTenant(7L);
    }

    @Test
    void suspenderEsIdempotenteYNoRevocaDeNuevoSiYaSuspendido() {
        Tenant t = tenantConEstado(7L, Tenant.ESTADO_SUSPENDIDO);
        when(tenantRepository.findById(7L)).thenReturn(Optional.of(t));
        when(reservaRepository.countByTenantId(7L)).thenReturn(0L);

        service.suspender(7L, 1L);

        assertEquals(Tenant.ESTADO_SUSPENDIDO, t.getEstado());
        verify(refreshTokenRepository, never()).revocarTodosDeTenant(anyLong());
    }

    @Test
    void noSePuedeSuspenderUnNegocioCerrado() {
        Tenant t = tenantConEstado(7L, Tenant.ESTADO_CERRADO);
        when(tenantRepository.findById(7L)).thenReturn(Optional.of(t));

        assertThrows(IllegalArgumentException.class, () -> service.suspender(7L, 1L));
        verify(refreshTokenRepository, never()).revocarTodosDeTenant(anyLong());
    }

    @Test
    void reactivarMueveSuspendidoAActivo() {
        Tenant t = tenantConEstado(7L, Tenant.ESTADO_SUSPENDIDO);
        when(tenantRepository.findById(7L)).thenReturn(Optional.of(t));
        when(reservaRepository.countByTenantId(7L)).thenReturn(1L);

        NegocioAdminResumen resp = service.reactivar(7L, 1L);

        assertEquals(Tenant.ESTADO_ACTIVO, t.getEstado());
        assertEquals(Tenant.ESTADO_ACTIVO, resp.estado());
    }

    @Test
    void noSePuedeReactivarUnNegocioCerrado() {
        Tenant t = tenantConEstado(7L, Tenant.ESTADO_CERRADO);
        when(tenantRepository.findById(7L)).thenReturn(Optional.of(t));

        assertThrows(IllegalArgumentException.class, () -> service.reactivar(7L, 1L));
    }

    @Test
    void detalleDeNegocioInexistenteFalla() {
        when(tenantRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.detalle(99L));
    }

    @Test
    void metricasAgregaLosKpisDePlataforma() {
        when(tenantRepository.count()).thenReturn(10L);
        when(tenantRepository.countByEstado(Tenant.ESTADO_ACTIVO)).thenReturn(7L);
        when(tenantRepository.countByEstado(Tenant.ESTADO_SUSPENDIDO)).thenReturn(2L);
        when(tenantRepository.countByEstado(Tenant.ESTADO_CERRADO)).thenReturn(1L);
        when(reservaRepository.count()).thenReturn(120L);
        when(reservaRepository.countByEstadoIn(EstadoReserva.ACTIVOS)).thenReturn(15L);
        when(cuentaClienteRepository.count()).thenReturn(48L);
        when(pagoRepository.totalRecaudadoPlataforma()).thenReturn(350000L);

        MetricasGlobales m = service.metricas();

        assertEquals(10L, m.negociosTotal());
        assertEquals(7L, m.negociosActivos());
        assertEquals(2L, m.negociosSuspendidos());
        assertEquals(1L, m.negociosCerrados());
        assertEquals(120L, m.reservasTotal());
        assertEquals(15L, m.reservasActivas());
        assertEquals(48L, m.apoderados());
        assertEquals(350000L, m.recaudadoSenasClp());
    }

    @Test
    void suspenderRegistraAuditoriaConElActor() {
        Tenant t = tenantConEstado(7L, Tenant.ESTADO_ACTIVO);
        when(tenantRepository.findById(7L)).thenReturn(Optional.of(t));
        when(reservaRepository.countByTenantId(7L)).thenReturn(0L);

        service.suspender(7L, 99L);

        ArgumentCaptor<AdminAuditLog> captor = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertEquals(99L, captor.getValue().getAdministradorId());
        assertEquals(AdminAuditLog.SUSPENDER_NEGOCIO, captor.getValue().getAccion());
        assertEquals(7L, captor.getValue().getTenantId());
    }

    @Test
    void crearAdminGuardaConHashYRegistraAuditoria() {
        when(administradorRepository.existsByEmail("nuevo@rk.cl")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("HASH");
        when(administradorRepository.save(any(Administrador.class))).thenAnswer(inv -> {
            Administrador a = inv.getArgument(0);
            ReflectionTestUtils.setField(a, "id", 5L);
            return a;
        });

        AdminResumen resp = service.crearAdmin(new CrearAdminRequest("Nuevo", "Nuevo@RK.cl", "password123"), 1L);

        assertEquals("nuevo@rk.cl", resp.email()); // normalizado a minúsculas
        ArgumentCaptor<Administrador> captor = ArgumentCaptor.forClass(Administrador.class);
        verify(administradorRepository).save(captor.capture());
        assertEquals("HASH", captor.getValue().getPasswordHash()); // nunca la password en claro
        verify(auditLogRepository).save(any(AdminAuditLog.class));
    }

    @Test
    void crearAdminRechazaEmailDuplicado() {
        when(administradorRepository.existsByEmail("dup@rk.cl")).thenReturn(true);
        assertThrows(IllegalArgumentException.class,
                () -> service.crearAdmin(new CrearAdminRequest("Dup", "dup@rk.cl", "password123"), 1L));
        verify(administradorRepository, never()).save(any());
    }

    private Tenant tenantConEstado(Long id, String estado) {
        Tenant t = new Tenant();
        ReflectionTestUtils.setField(t, "id", id);
        t.setNombre("Fiestas Pepito");
        t.setSlug("fiestas-pepito");
        t.setEstado(estado);
        return t;
    }
}
