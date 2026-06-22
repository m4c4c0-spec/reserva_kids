package cl.reservakids.domain.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.transaction.annotation.Transactional;

/**
 * H2: implementación del upsert atómico de rate limiting (ver {@link RateLimitBucketRepositoryCustom}).
 * Spring Data engancha esta clase a {@code RateLimitBucketRepository} por la convención de
 * sufijo {@code Impl}.
 */
public class RateLimitBucketRepositoryImpl implements RateLimitBucketRepositoryCustom {

    @PersistenceContext
    private EntityManager em;

    // language=PostgreSQL
    private static final String UPSERT = """
            INSERT INTO rate_limit_bucket (ip, ruta_tipo, epoch_minuto, contador, max_permitido)
            VALUES (:ip, :rutaTipo, :epochMinuto, 1, :max)
            ON CONFLICT (ip, ruta_tipo) DO UPDATE SET
                contador = CASE WHEN rate_limit_bucket.epoch_minuto = :epochMinuto
                                THEN rate_limit_bucket.contador + 1
                                ELSE 1 END,
                epoch_minuto = :epochMinuto,
                max_permitido = :max
            RETURNING contador
            """;

    @Override
    @Transactional
    public int incrementarYContar(String ip, String rutaTipo, long epochMinuto, int maxPermitido) {
        Number contador = (Number) em.createNativeQuery(UPSERT)
                .setParameter("ip", ip)
                .setParameter("rutaTipo", rutaTipo)
                .setParameter("epochMinuto", epochMinuto)
                .setParameter("max", maxPermitido)
                .getSingleResult();
        return contador.intValue();
    }
}
