package cl.reservakids.application;

import cl.reservakids.application.dto.AdminDtos.NegocioAdminResumen;
import cl.reservakids.application.usecase.AdminService;
import cl.reservakids.domain.model.Tenant;
import cl.reservakids.domain.repository.RefreshTokenRepository;
import cl.reservakids.domain.repository.ReservaRepository;
import cl.reservakids.domain.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock TenantRepository tenantRepository;
    @Mock ReservaRepository reservaRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;

    @InjectMocks AdminService service;

    @Test
    void suspenderMueveEstadoYRevocaSesionesDelTenant() {
        Tenant t = tenantConEstado(7L, Tenant.ESTADO_ACTIVO);
        when(tenantRepository.findById(7L)).thenReturn(Optional.of(t));
        when(reservaRepository.countByTenantId(7L)).thenReturn(3L);

        NegocioAdminResumen resp = service.suspender(7L);

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

        service.suspender(7L);

        assertEquals(Tenant.ESTADO_SUSPENDIDO, t.getEstado());
        verify(refreshTokenRepository, never()).revocarTodosDeTenant(anyLong());
    }

    @Test
    void noSePuedeSuspenderUnNegocioCerrado() {
        Tenant t = tenantConEstado(7L, Tenant.ESTADO_CERRADO);
        when(tenantRepository.findById(7L)).thenReturn(Optional.of(t));

        assertThrows(IllegalArgumentException.class, () -> service.suspender(7L));
        verify(refreshTokenRepository, never()).revocarTodosDeTenant(anyLong());
    }

    @Test
    void reactivarMueveSuspendidoAActivo() {
        Tenant t = tenantConEstado(7L, Tenant.ESTADO_SUSPENDIDO);
        when(tenantRepository.findById(7L)).thenReturn(Optional.of(t));
        when(reservaRepository.countByTenantId(7L)).thenReturn(1L);

        NegocioAdminResumen resp = service.reactivar(7L);

        assertEquals(Tenant.ESTADO_ACTIVO, t.getEstado());
        assertEquals(Tenant.ESTADO_ACTIVO, resp.estado());
    }

    @Test
    void noSePuedeReactivarUnNegocioCerrado() {
        Tenant t = tenantConEstado(7L, Tenant.ESTADO_CERRADO);
        when(tenantRepository.findById(7L)).thenReturn(Optional.of(t));

        assertThrows(IllegalArgumentException.class, () -> service.reactivar(7L));
    }

    @Test
    void detalleDeNegocioInexistenteFalla() {
        when(tenantRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.detalle(99L));
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
