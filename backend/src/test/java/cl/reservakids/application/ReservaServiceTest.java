package cl.reservakids.application;

import cl.reservakids.application.dto.ReservaDtos.CotizarRequest;
import cl.reservakids.application.dto.ReservaDtos.PagoRequest;
import cl.reservakids.application.dto.ReservaDtos.SolicitudPublicaRequest;
import cl.reservakids.application.usecase.NotificacionPort;
import cl.reservakids.application.usecase.ReservaService;
import cl.reservakids.domain.exception.ConflictoBloqueException;
import cl.reservakids.domain.exception.RecursoNoEncontradoException;
import cl.reservakids.domain.model.*;
import cl.reservakids.domain.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservaServiceTest {

    @Mock TenantRepository tenantRepository;
    @Mock ServicioRepository servicioRepository;
    @Mock BloqueDisponibleRepository bloqueRepository;
    @Mock ClienteRepository clienteRepository;
    @Mock ReservaRepository reservaRepository;
    @Mock PagoRepository pagoRepository;
    @Mock NotificacionPort notificacion;

    @InjectMocks ReservaService service;

    private Tenant tenant;
    private Servicio servicio;

    @BeforeEach
    void setUp() {
        tenant = new Tenant();
        tenant.setId(1L);
        tenant.setSlug("fiestas-pepito");
        tenant.setNombre("Fiestas Pepito");

        servicio = new Servicio();
        servicio.setId(10L);
        servicio.setTenantId(1L);
        servicio.setActivo(true);
    }

    private SolicitudPublicaRequest solicitud() {
        return new SolicitudPublicaRequest(10L, 20L, "Ana", "+56 9 1111 1111",
                "ana@mail.cl", 15, "Victoria", "Tema dinosaurios", true);
    }

    @Test
    void solicitudPublicaTomaElBloqueYCreaReservaPendiente() {
        when(tenantRepository.findBySlugAndEstado("fiestas-pepito", "ACTIVO")).thenReturn(Optional.of(tenant));
        when(servicioRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(servicio));
        when(bloqueRepository.transicionarEstado(20L, 1L, EstadoBloque.DISPONIBLE, EstadoBloque.EN_ESPERA))
                .thenReturn(1);
        // Falla #9 (2 años): se busca y guarda con el teléfono NORMALIZADO (E.164 sin '+')
        when(clienteRepository.findByTenantIdAndTelefono(1L, "56911111111")).thenReturn(Optional.empty());
        when(clienteRepository.save(any())).thenAnswer(inv -> {
            Cliente c = inv.getArgument(0);
            assertEquals("56911111111", c.getTelefono());
            c.setId(30L);
            return c;
        });
        when(reservaRepository.saveAndFlush(any())).thenAnswer(inv -> {
            Reserva r = inv.getArgument(0);
            r.setId(40L);
            return r;
        });
        when(pagoRepository.totalPagado(40L)).thenReturn(0);

        var respuesta = service.crearSolicitudPublica("fiestas-pepito", solicitud());

        assertEquals("PENDIENTE", respuesta.estado());
        verify(notificacion).nuevaSolicitud(eq(tenant), any(Reserva.class), any(Cliente.class));
    }

    @Test
    void solicitudSobreBloqueTomadoLanza409() {
        // RNF-05: el UPDATE condicionado no afecta filas → conflicto, sin crear nada
        when(tenantRepository.findBySlugAndEstado(any(), any())).thenReturn(Optional.of(tenant));
        when(servicioRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(servicio));
        when(bloqueRepository.transicionarEstado(20L, 1L, EstadoBloque.DISPONIBLE, EstadoBloque.EN_ESPERA))
                .thenReturn(0);

        assertThrows(ConflictoBloqueException.class,
                () -> service.crearSolicitudPublica("fiestas-pepito", solicitud()));
        verify(reservaRepository, never()).saveAndFlush(any());
        verify(notificacion, never()).nuevaSolicitud(any(), any(), any());
    }

    @Test
    void cotizarRechazaSeniaMayorAlTotal() {
        assertThrows(IllegalArgumentException.class,
                () -> service.cotizar(1L, 40L, new CotizarRequest(100_000, 150_000)));
    }

    @Test
    void cotizarSoloReservasDelTenant() {
        // Aislamiento multi-tenant: la reserva de otro tenant no es visible
        when(reservaRepository.findByIdAndTenantId(40L, 99L)).thenReturn(Optional.empty());
        assertThrows(RecursoNoEncontradoException.class,
                () -> service.cotizar(99L, 40L, new CotizarRequest(100_000, 30_000)));
    }

    @Test
    void confirmarMarcaElBloqueComoConfirmado() {
        Reserva reserva = reservaEnEstado(EstadoReserva.COTIZADA);
        when(reservaRepository.findByIdAndTenantId(40L, 1L)).thenReturn(Optional.of(reserva));
        when(pagoRepository.totalPagado(40L)).thenReturn(30_000);
        when(clienteRepository.findById(30L)).thenReturn(Optional.empty());

        var respuesta = service.confirmar(1L, 40L);

        assertEquals("CONFIRMADA", respuesta.estado());
        verify(bloqueRepository).transicionarEstado(20L, 1L, EstadoBloque.EN_ESPERA, EstadoBloque.CONFIRMADO);
    }

    /** Fix #4 (revisión de código): una devolución no puede superar lo efectivamente pagado. */
    @Test
    void devolucionMayorAlPagadoRechazada() {
        Reserva reserva = reservaEnEstado(EstadoReserva.CANCELADA);
        when(reservaRepository.findByIdAndTenantId(40L, 1L)).thenReturn(Optional.of(reserva));
        when(pagoRepository.totalPagado(40L)).thenReturn(30_000);

        assertThrows(IllegalArgumentException.class, () -> service.registrarPago(1L, 9L, 40L,
                new PagoRequest(50_000, "TRANSFERENCIA", null, "DEVOLUCION")));
        verify(pagoRepository, never()).save(any());
    }

    /** Fix #7 (revisión de código): la bandeja carga clientes y pagos por lote, no por reserva. */
    @Test
    void listarNoHaceConsultasPorReserva() {
        Reserva r1 = reservaEnEstado(EstadoReserva.PENDIENTE);
        Reserva r2 = reservaEnEstado(EstadoReserva.COTIZADA);
        r2.setId(41L);
        var pageable = org.springframework.data.domain.PageRequest.of(0, 20);
        when(reservaRepository.findByTenantIdOrderByCreadaEnDesc(1L, pageable))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(r1, r2), pageable, 2));
        when(pagoRepository.totalesPagadosPorReserva(java.util.List.of(40L, 41L)))
                .thenReturn(java.util.List.<Object[]>of(new Object[]{40L, 30_000L}));
        Cliente cliente = new Cliente();
        cliente.setId(30L);
        cliente.setNombre("Ana");
        cliente.setTelefono("56911111111");
        when(clienteRepository.findAllById(java.util.Set.of(30L))).thenReturn(java.util.List.of(cliente));

        var pagina = service.listar(1L, null, pageable);

        assertEquals(30_000, pagina.getContent().get(0).pagadoClp());
        assertEquals(0, pagina.getContent().get(1).pagadoClp());
        verify(pagoRepository, never()).totalPagado(anyLong());
        verify(clienteRepository, never()).findById(anyLong());
    }

    /** Falla #2 (revisión 2 años): REALIZADA dejó de ser inalcanzable. */
    @Test
    void realizarCierraUnaReservaConfirmada() {
        Reserva reserva = reservaEnEstado(EstadoReserva.CONFIRMADA);
        when(reservaRepository.findByIdAndTenantId(40L, 1L)).thenReturn(Optional.of(reserva));
        when(pagoRepository.totalPagado(40L)).thenReturn(100_000);
        when(clienteRepository.findById(30L)).thenReturn(Optional.empty());

        var respuesta = service.realizar(1L, 40L);

        assertEquals("REALIZADA", respuesta.estado());
        verifyNoInteractions(bloqueRepository); // el bloque pasado se queda CONFIRMADO
    }

    @Test
    void cancelarLiberaElBloque() {
        Reserva reserva = reservaEnEstado(EstadoReserva.PENDIENTE);
        when(reservaRepository.findByIdAndTenantId(40L, 1L)).thenReturn(Optional.of(reserva));
        when(pagoRepository.totalPagado(40L)).thenReturn(0);
        when(clienteRepository.findById(30L)).thenReturn(Optional.empty());

        var respuesta = service.cancelar(1L, 40L, null);

        assertEquals("CANCELADA", respuesta.estado());
        verify(bloqueRepository).transicionarEstado(20L, 1L, EstadoBloque.EN_ESPERA, EstadoBloque.DISPONIBLE);
    }

    private Reserva reservaEnEstado(EstadoReserva estado) {
        Reserva reserva = new Reserva();
        reserva.setId(40L);
        reserva.setTenantId(1L);
        reserva.setClienteId(30L);
        reserva.setServicioId(10L);
        reserva.setBloqueId(20L);
        reserva.setEstado(estado);
        return reserva;
    }
}
