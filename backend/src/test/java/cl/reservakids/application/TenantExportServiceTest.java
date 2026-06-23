package cl.reservakids.application;

import cl.reservakids.application.dto.TenantDtos.ExportResponse;
import cl.reservakids.application.usecase.TenantExportService;
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
import static org.mockito.Mockito.*;

/** Portabilidad (Ley 21.719): el export incluye todas las colecciones del tenant. */
@ExtendWith(MockitoExtension.class)
class TenantExportServiceTest {

    @Mock TenantRepository tenantRepository;
    @Mock ServicioRepository servicioRepository;
    @Mock ClienteRepository clienteRepository;
    @Mock BloqueDisponibleRepository bloqueRepository;
    @Mock ReservaRepository reservaRepository;
    @Mock PagoRepository pagoRepository;
    @Spy Clock clock = Clock.fixed(Instant.parse("2026-06-11T12:00:00Z"), ZoneOffset.UTC);

    @InjectMocks TenantExportService service;

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
}
