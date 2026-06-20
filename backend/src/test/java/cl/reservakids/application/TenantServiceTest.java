package cl.reservakids.application;

import cl.reservakids.application.dto.TenantDtos.CerrarRequest;
import cl.reservakids.application.dto.TenantDtos.ExportResponse;
import cl.reservakids.application.usecase.TenantExportService;
import cl.reservakids.application.usecase.TenantService;
import cl.reservakids.domain.model.*;
import cl.reservakids.domain.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/** Falla 3.3 (revisión a 5 años): offboarding de tenant — cierre a demanda. */
@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

    @Mock TenantRepository tenantRepository;
    @Mock BloqueDisponibleRepository bloqueRepository;
    @Mock ReservaRepository reservaRepository;
    @Mock UsuarioRepository usuarioRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock TenantExportService tenantExportService;
    @Spy Clock clock = Clock.fixed(Instant.parse("2026-06-11T12:00:00Z"), ZoneOffset.UTC);

    @InjectMocks TenantService service;

    @Test
    void cerrarCancelaAbiertasRevocaSesionesYDevuelveExport() {
        Tenant tenant = tenant("fiestas-pepito");
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(reservaRepository.existsByTenantIdAndEstado(1L, EstadoReserva.CONFIRMADA)).thenReturn(false);

        Reserva pendiente = reserva(10L, EstadoReserva.PENDIENTE, 20L);
        when(reservaRepository.findByTenantIdAndEstadoIn(
                1L, List.of(EstadoReserva.PENDIENTE, EstadoReserva.COTIZADA)))
                .thenReturn(List.of(pendiente));

        Usuario dueno = new Usuario();
        dueno.setId(5L);
        when(usuarioRepository.findByTenantId(1L)).thenReturn(List.of(dueno));
        // El export final lo produce TenantExportService (otra bean, misma transacción).
        when(tenantExportService.exportar(1L)).thenReturn(exportConEstado(Tenant.ESTADO_CERRADO));

        ExportResponse export = service.cerrar(1L, new CerrarRequest("fiestas-pepito"));

        assertEquals(Tenant.ESTADO_CERRADO, tenant.getEstado());
        assertNotNull(tenant.getCerradoEn());
        assertEquals(EstadoReserva.CANCELADA, pendiente.getEstado());
        assertTrue(pendiente.getComentarios().contains("[Cierre del negocio]"));
        verify(bloqueRepository).transicionarEstado(20L, 1L, EstadoBloque.EN_ESPERA, EstadoBloque.DISPONIBLE);
        verify(refreshTokenRepository).revocarTodosDeUsuario(5L);
        assertEquals(Tenant.ESTADO_CERRADO, export.estado()); // última copia: refleja el cierre
    }

    /** Con seña de por medio, devolver dinero es decisión humana: el cierre se rechaza. */
    @Test
    void cerrarRechazadoConReservasConfirmadas() {
        Tenant tenant = tenant("fiestas-pepito");
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(reservaRepository.existsByTenantIdAndEstado(1L, EstadoReserva.CONFIRMADA)).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> service.cerrar(1L, new CerrarRequest("fiestas-pepito")));
        assertEquals(Tenant.ESTADO_ACTIVO, tenant.getEstado());
        verify(refreshTokenRepository, never()).revocarTodosDeUsuario(anyLong());
    }

    /** Anti cierre accidental: la confirmación debe ser el slug exacto. */
    @Test
    void cerrarRechazadoSinConfirmacionDeSlug() {
        Tenant tenant = tenant("fiestas-pepito");
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));

        assertThrows(IllegalArgumentException.class,
                () -> service.cerrar(1L, new CerrarRequest("otro-slug")));
        assertEquals(Tenant.ESTADO_ACTIVO, tenant.getEstado());
    }

    @Test
    void cerrarDosVecesEsRechazado() {
        Tenant tenant = tenant("fiestas-pepito");
        tenant.cerrar(java.time.OffsetDateTime.parse("2026-06-01T00:00:00Z"));
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));

        assertThrows(IllegalArgumentException.class,
                () -> service.cerrar(1L, new CerrarRequest("fiestas-pepito")));
    }

    private Tenant tenant(String slug) {
        Tenant tenant = new Tenant();
        ReflectionTestUtils.setField(tenant, "id", 1L);
        tenant.setNombre("Fiestas Pepito");
        tenant.setSlug(slug);
        return tenant;
    }

    private Reserva reserva(Long id, EstadoReserva estado, Long bloqueId) {
        Reserva reserva = new Reserva();
        reserva.setId(id);
        reserva.setTenantId(1L);
        reserva.setClienteId(30L);
        reserva.setServicioId(40L);
        reserva.setBloqueId(bloqueId);
        reserva.setEstado(estado);
        return reserva;
    }

    private ExportResponse exportConEstado(String estado) {
        return new ExportResponse("Fiestas Pepito", "fiestas-pepito", null, estado,
                null, null, OffsetDateTime.now(),
                List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
