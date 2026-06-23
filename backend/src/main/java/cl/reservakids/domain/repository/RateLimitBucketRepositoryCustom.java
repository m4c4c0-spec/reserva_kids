package cl.reservakids.domain.repository;

/**
 * H2: contador de rate limiting ATÓMICO en la BD, válido entre múltiples instancias.
 *
 * Antes el conteo vivía en un {@code ConcurrentHashMap} por instancia (la BD solo era un
 * respaldo cada 30 s): con N réplicas el límite efectivo era N×configurado. Este upsert
 * atómico (INSERT … ON CONFLICT … RETURNING, bajo bloqueo de fila de Postgres) hace que
 * todas las instancias compartan el mismo contador.
 */
public interface RateLimitBucketRepositoryCustom {

    /**
     * Incrementa atómicamente el contador de la ventana del minuto actual para {@code (ip, rutaTipo)}
     * y devuelve el nuevo valor. Si la fila pertenece a un minuto anterior, reinicia a 1.
     */
    int incrementarYContar(String ip, String rutaTipo, long epochMinuto, int maxPermitido);
}
