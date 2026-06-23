package cl.reservakids.application.usecase;

import cl.reservakids.domain.model.EstadoBloque;
import cl.reservakids.domain.model.EstadoReserva;
import cl.reservakids.domain.model.Reserva;
import cl.reservakids.domain.repository.BloqueDisponibleRepository;
import cl.reservakids.domain.repository.ReservaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Jobs del ciclo de vida de una reserva.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CicloReservaJobs {

    private final ReservaRepository reservaRepository;
    private final BloqueDisponibleRepository bloqueRepository;
    private final Clock clock;

    @Value("${app.reservas.expiracion-horas}")
    private long expiracionHoras;

    @Value("${app.reservas.cotizacion-expiracion-dias}")
    private long cotizacionExpiracionDias;

    /** Minutos que una cita por hora retiene su franja esperando el pago antes de liberarla. */
    @Value("${app.citas.pago-expiracion-min:30}")
    private long citaPagoExpiracionMin;

    /**
     * Cita por hora creada pero no pagada: tras N minutos se cancela y la franja vuelve a estar
     * libre (la ocupación solo cuenta PENDIENTE_PAGO/CONFIRMADA, así que cancelarla la libera).
     * Sin esto, un cliente que abandona el checkout dejaría la hora bloqueada para siempre.
     */
    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT3M")
    @Transactional
    public void expirarCitasSinPago() {
        OffsetDateTime limite = OffsetDateTime.now(clock).minusMinutes(citaPagoExpiracionMin);
        List<Reserva> vencidas = reservaRepository
                .findByEstadoAndInicioIsNotNullAndCreadaEnBefore(EstadoReserva.PENDIENTE_PAGO, limite);

        for (Reserva reserva : vencidas) {
            cancelarPorExpiracion(reserva,
                    "[Expiración] Cita cancelada por falta de pago tras %d min.".formatted(citaPagoExpiracionMin));
        }
        if (!vencidas.isEmpty()) {
            log.info("Job de expiración: {} citas sin pago canceladas", vencidas.size());
        }
    }

    /** Corre cada 15 minutos; idempotente, opera sobre todos los tenants. */
    @Scheduled(fixedDelayString = "PT15M", initialDelayString = "PT1M")
    @Transactional
    public void expirarPendientes() {
        OffsetDateTime limite = OffsetDateTime.now(clock).minusHours(expiracionHoras);
        List<Reserva> vencidas = reservaRepository
                .findByEstadoAndCreadaEnBefore(EstadoReserva.PENDIENTE, limite);

        for (Reserva reserva : vencidas) {
            cancelarPorExpiracion(reserva,
                    "[Expiración] Cancelada automáticamente tras %d h sin cotización.".formatted(expiracionHoras));
        }
        if (!vencidas.isEmpty()) {
            log.info("Job de expiración: {} solicitudes pendientes canceladas", vencidas.size());
        }
    }

    /**
     * Falla #1 (revisión a 2 años): una COTIZADA cuyo cliente desapareció retenía su bloque
     * EN_ESPERA para siempre — sábados "ocupados" por cotizaciones fantasma de hace meses.
     * Pasados N días sin confirmación, se cancela y el bloque vuelve a la venta.
     */
    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT2M")
    @Transactional
    public void expirarCotizadas() {
        OffsetDateTime limite = OffsetDateTime.now(clock).minusDays(cotizacionExpiracionDias);
        List<Reserva> vencidas = reservaRepository
                .findByEstadoAndCotizadaEnBefore(EstadoReserva.COTIZADA, limite);

        for (Reserva reserva : vencidas) {
            cancelarPorExpiracion(reserva,
                    "[Expiración] Cotización sin respuesta tras %d días.".formatted(cotizacionExpiracionDias));
        }
        if (!vencidas.isEmpty()) {
            log.info("Job de expiración: {} cotizaciones sin respuesta canceladas", vencidas.size());
        }
    }

    /**
     * Falla #2 (revisión a 2 años): el estado REALIZADA era inalcanzable — toda fiesta concretada
     * quedaba CONFIRMADA (es decir, "activa") para siempre, bloqueando la anonimización del
     * cliente y ensuciando los reportes. Barrido diario: confirmadas con fecha ya pasada.
     */
    @Scheduled(cron = "0 15 4 * * *")
    @Transactional
    public void realizarConcluidas() {
        LocalDate hoy = LocalDate.now(clock);
        List<Reserva> concluidas = reservaRepository
                .findByEstadoConBloqueAnterior(EstadoReserva.CONFIRMADA, hoy);

        for (Reserva reserva : concluidas) {
            reserva.transicionarA(EstadoReserva.REALIZADA);
        }
        if (!concluidas.isEmpty()) {
            log.info("Job de cierre: {} reservas confirmadas marcadas REALIZADA", concluidas.size());
        }
    }

    private void cancelarPorExpiracion(Reserva reserva, String nota) {
        reserva.transicionarA(EstadoReserva.CANCELADA);
        String previos = reserva.getComentarios() == null ? "" : reserva.getComentarios() + "\n";
        reserva.setComentarios(previos + nota);
        bloqueRepository.transicionarEstado(reserva.getBloqueId(), reserva.getTenantId(),
                EstadoBloque.EN_ESPERA, EstadoBloque.DISPONIBLE);
        log.info("Reserva #{} expirada; bloque {} liberado", reserva.getId(), reserva.getBloqueId());
    }
}
