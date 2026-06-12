package cl.reservakids.application;

import cl.reservakids.application.dto.TenantDtos.CerrarRequest;
import cl.reservakids.application.dto.TenantDtos.ExportResponse;
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
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/** Falla 3.3 (revisión a 5 años): offboarding de tenant — export y cierre a demanda. */
@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

    @Mock TenantRepository tenantRepository;
    @Mock ServicioRepository servicioRepository;
    @Mock ClienteRepository clienteRepository;
    @Mock BloqueDisponibleRepository bloqueRepository;
    @Mock ReservaRepository reservaRepository;
    @Mock PagoRepository pagoRepository;
    @Mock UsuarioRepository usuarioRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
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
        exportVacio();

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

    /** Portabilidad (Ley 21.719): el export incluye todas las colecciones del tenant. */
    @Test
    void exportIncluyeTodasLasColecciones() {
        Tenant tenant = tenant("fiestas-pepito");
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));

        Servicio servicio = new Servicio();
        servicio.setNombre("Cumpleaños básico");
        servicio.setPrecioClp(50000);
        when(servicioRepository.findByTenantIdOrderByNombre(1L)).thenReturn(List.of(servicio));
        Cliente cliente = new Cliente();
        cliente.setNombre("Ana");
        cliente.setTelefono("56911111111");
        when(clienteRepository.findByTenantIdOrderByNombre(1L)).thenReturn(List.of(cliente));
        BloqueDisponible bloque = new BloqueDisponible();
        bloque.setFecha(java.time.LocalDate.parse("2026-07-04"));
        bloque.setHoraInicio(java.time.LocalTime.NOON);
        bloque.setHoraFin(java.time.LocalTime.of(15, 0));
        when(bloqueRepository.findByTenantIdOrderByFechaAscHoraInicioAsc(1L)).thenReturn(List.of(bloque));
        when(reservaRepository.findByTenantIdOrderByCreadaEnDesc(1L))
                .thenReturn(List.of(reserva(10L, EstadoReserva.REALIZADA, 20L)));
        Pago pago = new Pago();
        pago.setReservaId(10L);
        pago.setMontoClp(20000);
        pago.setMedio("TRANSFERENCIA");
        when(pagoRepository.findDeTenant(1L)).thenReturn(List.of(pago));

        ExportResponse export = service.exportar(1L);

        assertEquals("fiestas-pepito", export.slug());
        assertEquals(1, export.servicios().size());
        assertEquals(1, export.clientes().size());
        assertEquals(1, export.bloques().size());
        assertEquals(1, export.reservas().size());
        assertEquals(1, export.pagos().size());
        assertNotNull(export.generadoEn());
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

    private void exportVacio() {
        when(servicioRepository.findByTenantIdOrderByNombre(anyLong())).thenReturn(List.of());
        when(clienteRepository.findByTenantIdOrderByNombre(anyLong())).thenReturn(List.of());
        when(bloqueRepository.findByTenantIdOrderByFechaAscHoraInicioAsc(anyLong())).thenReturn(List.of());
        when(reservaRepository.findByTenantIdOrderByCreadaEnDesc(anyLong())).thenReturn(List.of());
        when(pagoRepository.findDeTenant(anyLong())).thenReturn(List.of());
    }
}
