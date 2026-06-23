package cl.reservakids.application.usecase;

import cl.reservakids.domain.model.EstadoBloque;
import cl.reservakids.domain.repository.BloqueDisponibleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Limpieza de bloques de disponibilidad obsoletos.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BloqueMantenimientoJobs {

    private final BloqueDisponibleRepository bloqueRepository;
    private final Clock clock;

    /**
     * Falla #11 (revisión a 2 años): bloques DISPONIBLE con fecha pasada y sin ninguna reserva
     * que los referencie son solo ruido histórico — se eliminan los lunes a las 04:45.
     */
    @Scheduled(cron = "0 45 4 * * MON")
    @Transactional
    public void limpiarBloquesPasados() {
        int eliminados = bloqueRepository.eliminarPasadosSinReserva(LocalDate.now(clock), EstadoBloque.DISPONIBLE);
        if (eliminados > 0) {
            log.info("Mantenimiento: {} bloques pasados sin reservas eliminados", eliminados);
        }
    }
}
