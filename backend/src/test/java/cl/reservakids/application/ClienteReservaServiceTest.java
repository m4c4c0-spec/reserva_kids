package cl.reservakids.application;

import cl.reservakids.application.dto.ReservaDtos.ReservaResponse;
import cl.reservakids.application.usecase.NotificacionPort;
import cl.reservakids.application.usecase.NotificacionWhatsappPort;
import cl.reservakids.application.usecase.ReservaService;
import cl.reservakids.domain.model.Cliente;
import cl.reservakids.domain.model.EstadoReserva;
import cl.reservakids.domain.model.Reserva;
import cl.reservakids.domain.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Sprint 2 §2.2: historial de reservas del cliente autenticado (apoderado). Verifica que
 * {@link ReservaService#listarReservasDeCliente} consulta por {@code cuentaClienteId} (no por
 * tenant — el apoderado cruza negocios), filtra por estado cuando se pide y carga pagos y
 * clientes por lote (anti-N+1, mismo patrón que {@code listar}).
 */
@ExtendWith(MockitoExtension.class)
class ClienteReservaServiceTest {

    @Mock TenantRepository tenantRepository;
    @Mock ServicioRepository servicioRepository;
    @Mock BloqueDisponibleRepository bloqueRepository;
    @Mock ClienteRepository clienteRepository;
    @Mock ReservaRepository reservaRepository;
    @Mock ReservaServicioRepository reservaServicioRepository;
    @Mock PagoRepository pagoRepository;
    @Mock NotificacionPort notificacion;
    @Mock NotificacionWhatsappPort whatsapp;

    @InjectMocks ReservaService service;

    private Reserva cita(Long id, Long cuentaClienteId, Long clienteId, EstadoReserva estado) {
        Reserva r = new Reserva();
        r.setId(id);
        r.setTenantId(1L);
        r.setClienteId(clienteId);
        r.setCuentaClienteId(cuentaClienteId);
        r.setEstado(estado);
        r.setTotalClp(20_000);
        r.setInicio(OffsetDateTime.now().plusDays(1));
        r.setFin(r.getInicio().plusHours(1));
        return r;
    }

    @Test
    void listarSinFiltroUsaQueryPorCuentaCliente() {
        Reserva r1 = cita(40L, 7L, 30L, EstadoReserva.CONFIRMADA);
        Reserva r2 = cita(41L, 7L, 31L, EstadoReserva.PENDIENTE_PAGO);
        var pageable = PageRequest.of(0, 20);
        when(reservaRepository.findByCuentaClienteIdOrderByCreadaEnDesc(7L, pageable))
                .thenReturn(new PageImpl<>(List.of(r1, r2), pageable, 2));
        when(pagoRepository.totalesPagadosPorReserva(List.of(40L, 41L)))
                .thenReturn(List.<Object[]>of(new Object[]{40L, 20_000L}));
        Cliente c1 = new Cliente();
        c1.setId(30L);
        c1.setNombre("Ana");
        c1.setTelefono("56911111111");
        Cliente c2 = new Cliente();
        c2.setId(31L);
        c2.setNombre("Beto");
        c2.setTelefono("56922222222");
        when(clienteRepository.findAllById(Set.of(30L, 31L))).thenReturn(List.of(c1, c2));

        Page<ReservaResponse> pagina = service.listarReservasDeCliente(7L, null, pageable);

        assertEquals(2, pagina.getContent().size());
        assertEquals(20_000, pagina.getContent().get(0).pagadoClp());
        assertEquals(0, pagina.getContent().get(1).pagadoClp());
        verify(reservaRepository).findByCuentaClienteIdOrderByCreadaEnDesc(7L, pageable);
        verify(reservaRepository, never()).findByCuentaClienteIdAndEstadoOrderByCreadaEnDesc(anyLong(), any(), any());
    }

    @Test
    void listarConFiltroDeEstadoUsaQueryFiltrada() {
        Reserva r1 = cita(40L, 7L, 30L, EstadoReserva.CONFIRMADA);
        var pageable = PageRequest.of(0, 20);
        when(reservaRepository.findByCuentaClienteIdAndEstadoOrderByCreadaEnDesc(7L, EstadoReserva.CONFIRMADA, pageable))
                .thenReturn(new PageImpl<>(List.of(r1), pageable, 1));
        when(pagoRepository.totalesPagadosPorReserva(List.of(40L)))
                .thenReturn(List.<Object[]>of());
        when(clienteRepository.findAllById(Set.of(30L))).thenReturn(List.of());

        var pagina = service.listarReservasDeCliente(7L, EstadoReserva.CONFIRMADA, pageable);

        assertEquals(1, pagina.getContent().size());
        assertEquals("CONFIRMADA", pagina.getContent().get(0).estado());
        verify(reservaRepository).findByCuentaClienteIdAndEstadoOrderByCreadaEnDesc(7L, EstadoReserva.CONFIRMADA, pageable);
        verify(reservaRepository, never()).findByCuentaClienteIdOrderByCreadaEnDesc(anyLong(), any());
    }

    @Test
    void listarSinReservasDevuelvePaginaVacia() {
        var pageable = PageRequest.of(0, 20);
        when(reservaRepository.findByCuentaClienteIdOrderByCreadaEnDesc(99L, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        var pagina = service.listarReservasDeCliente(99L, null, pageable);

        assertTrue(pagina.isEmpty());
        verifyNoInteractions(pagoRepository, clienteRepository);
    }

    @Test
    void listarNoHaceConsultasPorReserva() {
        Reserva r1 = cita(40L, 7L, 30L, EstadoReserva.CONFIRMADA);
        Reserva r2 = cita(41L, 7L, 30L, EstadoReserva.PENDIENTE_PAGO);
        var pageable = PageRequest.of(0, 20);
        when(reservaRepository.findByCuentaClienteIdOrderByCreadaEnDesc(7L, pageable))
                .thenReturn(new PageImpl<>(List.of(r1, r2), pageable, 2));
        when(pagoRepository.totalesPagadosPorReserva(List.of(40L, 41L)))
                .thenReturn(List.<Object[]>of(new Object[]{40L, 15_000L}));
        Cliente cliente = new Cliente();
        cliente.setId(30L);
        cliente.setNombre("Ana");
        cliente.setTelefono("56911111111");
        when(clienteRepository.findAllById(Set.of(30L))).thenReturn(List.of(cliente));

        service.listarReservasDeCliente(7L, null, pageable);

        verify(pagoRepository, never()).totalPagado(anyLong());
        verify(clienteRepository, never()).findByIdAndTenantId(anyLong(), anyLong());
    }
}
