package cl.reservakids.infrastructure.adapter;

import cl.reservakids.application.usecase.DistributedLockPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Lock distribuido basado en PostgreSQL pg_try_advisory_lock.
 * No bloqueante: si otra instancia ya tiene el lock, retorna false inmediatamente.
 * Se libera automáticamente al final de la transacción (advisory lock de sesión).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostgresDistributedLock implements DistributedLockPort {

    private final JdbcTemplate jdbc;

    @Override
    public boolean tryAcquire(String lockKey) {
        Long result = jdbc.queryForObject(
                "SELECT pg_try_advisory_lock(hashtext(?))", Long.class, lockKey);
        boolean acquired = result != null && result == 1L;
        if (acquired) {
            log.debug("Lock distribuido adquirido: {}", lockKey);
        }
        return acquired;
    }
}
