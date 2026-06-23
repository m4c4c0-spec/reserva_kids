package cl.reservakids.application.usecase;

/**
 * Puerto de trazabilidad (RNF-07). Registra acciones de seguridad relevantes
 * en una bitácora append-only para auditoría y no repudio.
 */
public interface AuditPort {

    void registrar(Long tenantId, Long actorId, String actorType, String accion,
                   String recursoTipo, Long recursoId, String detalle);
}
