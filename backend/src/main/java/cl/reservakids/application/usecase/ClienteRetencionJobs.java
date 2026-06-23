package cl.reservakids.application.usecase;

import cl.reservakids.domain.model.Cliente;
import cl.reservakids.domain.model.EstadoReserva;
import cl.reservakids.domain.model.Reserva;
import cl.reservakids.domain.repository.ClienteRepository;
import cl.reservakids.domain.repository.ReservaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;

/**
 * Retención de datos de clientes (Ley 21.719 — minimización).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClienteRetencionJobs {

    private final ClienteRepository clienteRepository;
    private final ReservaRepository reservaRepository;
    private final Clock clock;

    @Value("${app.clientes.retencion-meses}")
    private long retencionClienteMeses;

    /**
     * Falla #4 (revisión a 2 años, Ley 21.719 — minimización): clientes sin actividad por más
     * de la retención configurada se anonimizan (la fila se conserva: las reservas históricas
     * la referencian, pero deja de contener datos personales). Mensual, día 1 a las 05:00.
     * Un cliente con reservas activas todavía no es candidato — por eso depende del barrido
     * de {@link CicloReservaJobs#realizarConcluidas()}.
     */
    @Scheduled(cron = "0 0 5 1 * *")
    @Transactional
    public void anonimizarInactivos() {
        OffsetDateTime ahora = OffsetDateTime.now(clock);
        OffsetDateTime limite = ahora.minusMonths(retencionClienteMeses);
        int anonimizados = 0;

        for (Cliente cliente : clienteRepository.findByAnonimizadoEnIsNullAndUltimaActividadEnBefore(limite)) {
            if (reservaRepository.existsByClienteIdAndEstadoIn(cliente.getId(), EstadoReserva.ACTIVOS)) {
                continue; // todavía tiene reservas vigentes: su actividad no terminó
            }
            cliente.anonimizar(ahora);
            // Falla 3.2 (5 años): los comentarios de sus reservas también son datos personales
            reservaRepository.anonimizarComentariosDeCliente(
                    cliente.getId(), Reserva.COMENTARIOS_ANONIMIZADOS);
            anonimizados++;
        }
        if (anonimizados > 0) {
            log.info("Ley 21.719: {} clientes anonimizados por inactividad (> {} meses)",
                    anonimizados, retencionClienteMeses);
        }
    }
}
