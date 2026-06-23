package cl.reservakids.application;

import cl.reservakids.application.usecase.CicloReservaJobs;
import cl.reservakids.domain.model.EstadoBloque;
import cl.reservakids.domain.model.EstadoReserva;
import cl.reservakids.domain.model.Reserva;
import cl.reservakids.domain.repository.BloqueDisponibleRepository;
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
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CicloReservaJobsTest {

    @Mock ReservaRepository reservaRepository;
    @Mock BloqueDisponibleRepository bloqueRepository;
    @Spy Clock clock = Clock.fixed(Instant.parse("2026-06-10T12:00:00Z"), ZoneOffset.UTC);

    @InjectMocks CicloReservaJobs jobs;

    @Test
    void cancelaPendientesVencidasYLiberaBloques() {
        ReflectionTestUtils.setField(jobs, "expiracionHoras", 48L);

        Reserva vencida = new Reserva();
        vencida.setId(1L);
        vencida.setTenantId(7L);
        vencida.setBloqueId(20L);
        when(reservaRepository.findByEstadoAndCreadaEnBefore(eq(EstadoReserva.PENDIENTE), any()))
                .thenReturn(List.of(vencida));

        jobs.expirarPendientes();

        assertEquals(EstadoReserva.CANCELADA, vencida.getEstado());
        assertTrue(vencida.getComentarios().contains("[Expiración]"));
        verify(bloqueRepository).transicionarEstado(20L, 7L, EstadoBloque.EN_ESPERA, EstadoBloque.DISPONIBLE);
    }

    @Test
    void cancelaCitasSinPagoYLiberaBloques() {
        ReflectionTestUtils.setField(jobs, "citaPagoExpiracionMin", 30L);

        Reserva vencida = new Reserva();
        vencida.setId(1L);
        vencida.setTenantId(7L);
        vencida.setBloqueId(20L);
        when(reservaRepository.findByEstadoAndInicioIsNotNullAndCreadaEnBefore(eq(EstadoReserva.PENDIENTE_PAGO), any()))
                .thenReturn(List.of(vencida));

        jobs.expirarCitasSinPago();

        assertEquals(EstadoReserva.CANCELADA, vencida.getEstado());
        assertTrue(vencida.getComentarios().contains("falta de pago"));
        verify(bloqueRepository).transicionarEstado(20L, 7L, EstadoBloque.EN_ESPERA, EstadoBloque.DISPONIBLE);
    }

    /** Falla #1 (revisión 2 años): cotizadas sin respuesta liberan su bloque. */
    @Test
    void cancelaCotizadasSinRespuestaYLiberaBloques() {
        ReflectionTestUtils.setField(jobs, "cotizacionExpiracionDias", 14L);

        Reserva sinRespuesta = new Reserva();
        sinRespuesta.setId(2L);
        sinRespuesta.setTenantId(7L);
        sinRespuesta.setBloqueId(21L);
        sinRespuesta.setEstado(EstadoReserva.COTIZADA);
        when(reservaRepository.findByEstadoAndCotizadaEnBefore(eq(EstadoReserva.COTIZADA), any()))
                .thenReturn(List.of(sinRespuesta));

        jobs.expirarCotizadas();

        assertEquals(EstadoReserva.CANCELADA, sinRespuesta.getEstado());
        assertTrue(sinRespuesta.getComentarios().contains("[Expiración]"));
        verify(bloqueRepository).transicionarEstado(21L, 7L, EstadoBloque.EN_ESPERA, EstadoBloque.DISPONIBLE);
    }

    /** Falla #2 (revisión 2 años): confirmadas con fecha pasada se cierran como REALIZADA. */
    @Test
    void marcaRealizadasLasConfirmadasConFechaPasada() {
        Reserva concluida = new Reserva();
        concluida.setId(3L);
        concluida.setEstado(EstadoReserva.CONFIRMADA);
        when(reservaRepository.findByEstadoConBloqueAnterior(
                EstadoReserva.CONFIRMADA, LocalDate.parse("2026-06-10")))
                .thenReturn(List.of(concluida));

        jobs.realizarConcluidas();

        assertEquals(EstadoReserva.REALIZADA, concluida.getEstado());
        verifyNoInteractions(bloqueRepository); // el bloque pasado se queda CONFIRMADO
    }

    @Test
    void sinVencidasNoHaceNada() {
        ReflectionTestUtils.setField(jobs, "expiracionHoras", 48L);
        when(reservaRepository.findByEstadoAndCreadaEnBefore(any(), any())).thenReturn(List.of());

        jobs.expirarPendientes();

        verifyNoInteractions(bloqueRepository);
    }
}
