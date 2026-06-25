package cl.reservakids.application.usecase;

/**
 * Puerto para lock distribuido entre instancias del scheduler.
 * En entornos multi-instancia (horizontal scaling), garantiza que un job
 * solo se ejecute en una instancia a la vez usando pg_try_advisory_lock.
 */
public interface DistributedLockPort {

    /**
     * Intenta adquirir un lock no bloqueante para la clave dada.
     * @param lockKey clave identificadora del lock (ej. "expirarPendientes").
     * @return true si el lock fue adquirido (esta instancia ejecuta el job),
     *         false si otra instancia ya lo tiene.
     */
    boolean tryAcquire(String lockKey);
}
