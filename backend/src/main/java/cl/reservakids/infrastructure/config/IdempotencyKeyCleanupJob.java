package cl.reservakids.infrastructure.config;

import cl.reservakids.domain.repository.IdempotencyKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotencyKeyCleanupJob {

    private final IdempotencyKeyRepository idempotencyKeyRepository;

    /** Purga claves de idempotencia con más de 24 h cada hora. */
    @Scheduled(fixedDelay = 3_600_000)
    @Transactional
    public void purgarExpiradas() {
        OffsetDateTime corte = OffsetDateTime.now().minusHours(24);
        int borradas = idempotencyKeyRepository.purgarAnterioresA(corte);
        if (borradas > 0) {
            log.debug("Purgadas {} claves de idempotencia anteriores a {}", borradas, corte);
        }
    }
}
