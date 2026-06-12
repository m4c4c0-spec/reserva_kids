package cl.reservakids.application;

import cl.reservakids.application.usecase.ClienteService;
import cl.reservakids.domain.exception.RecursoNoEncontradoException;
import cl.reservakids.domain.model.Cliente;
import cl.reservakids.domain.model.EstadoReserva;
import cl.reservakids.domain.model.Reserva;
import cl.reservakids.domain.repository.ClienteRepository;
import cl.reservakids.domain.repository.ReservaRepository;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Falla #4 (revisión 2 años, Ley 21.719): derecho de supresión a demanda. */
@ExtendWith(MockitoExtension.class)
class ClienteServiceTest {

    @Mock ClienteRepository clienteRepository;
    @Mock ReservaRepository reservaRepository;
    @Spy Clock clock = Clock.fixed(Instant.parse("2026-06-10T12:00:00Z"), ZoneOffset.UTC);

    @InjectMocks ClienteService service;

    @Test
    void anonimizaClienteSinReservasActivas() {
        Cliente cliente = cliente(30L);
        when(clienteRepository.findByIdAndTenantId(30L, 1L)).thenReturn(Optional.of(cliente));
        when(reservaRepository.existsByClienteIdAndEstadoIn(30L, EstadoReserva.ACTIVOS)).thenReturn(false);

        service.anonimizar(1L, 30L);

        assertTrue(cliente.isAnonimizado());
        assertEquals(Cliente.NOMBRE_ANONIMO, cliente.getNombre());
        assertEquals("anon-30", cliente.getTelefono());
        assertNull(cliente.getEmail());
        assertNull(cliente.getConsentimientoEn());
        // Falla 3.2 (5 años): la supresión también limpia los comentarios de sus reservas
        verify(reservaRepository).anonimizarComentariosDeCliente(30L, Reserva.COMENTARIOS_ANONIMIZADOS);
    }

    @Test
    void rechazaSupresionConReservasActivas() {
        Cliente cliente = cliente(30L);
        when(clienteRepository.findByIdAndTenantId(30L, 1L)).thenReturn(Optional.of(cliente));
        when(reservaRepository.existsByClienteIdAndEstadoIn(30L, EstadoReserva.ACTIVOS)).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> service.anonimizar(1L, 30L));
        assertFalse(cliente.isAnonimizado());
        assertEquals("Ana", cliente.getNombre());
        verify(reservaRepository, never()).anonimizarComentariosDeCliente(any(), any());
    }

    @Test
    void esIdempotenteSiYaEstabaAnonimizado() {
        Cliente cliente = cliente(30L);
        cliente.anonimizar(OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        when(clienteRepository.findByIdAndTenantId(30L, 1L)).thenReturn(Optional.of(cliente));

        assertDoesNotThrow(() -> service.anonimizar(1L, 30L));
        verifyNoInteractions(reservaRepository);
    }

    @Test
    void soloClientesDelTenant() {
        // Aislamiento multi-tenant: el cliente de otro tenant no es visible
        when(clienteRepository.findByIdAndTenantId(30L, 99L)).thenReturn(Optional.empty());
        assertThrows(RecursoNoEncontradoException.class, () -> service.anonimizar(99L, 30L));
    }

    private Cliente cliente(Long id) {
        Cliente cliente = new Cliente();
        ReflectionTestUtils.setField(cliente, "id", id);
        cliente.setTenantId(1L);
        cliente.setNombre("Ana");
        cliente.setTelefono("56911111111");
        cliente.setEmail("ana@mail.cl");
        cliente.setConsentimientoEn(OffsetDateTime.parse("2026-06-01T00:00:00Z"));
        return cliente;
    }
}
