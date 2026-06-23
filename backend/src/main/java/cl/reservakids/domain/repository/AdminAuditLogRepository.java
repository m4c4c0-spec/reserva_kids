package cl.reservakids.domain.repository;

import cl.reservakids.domain.model.AdminAuditLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {

    /**
     * Bitácora reciente para la consola (F5), con el email del admin que ejecutó la acción
     * resuelto en el mismo query (sin N+1). Proyección {@link AuditoriaView}.
     */
    @Query("""
            SELECT l.creadoEn AS creadoEn, a.email AS adminEmail,
                   l.accion AS accion, l.detalle AS detalle
            FROM AdminAuditLog l JOIN Administrador a ON a.id = l.administradorId
            ORDER BY l.creadoEn DESC""")
    List<AuditoriaView> listarReciente(Pageable pageable);
}
