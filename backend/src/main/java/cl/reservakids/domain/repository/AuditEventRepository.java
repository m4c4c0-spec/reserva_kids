package cl.reservakids.domain.repository;

import cl.reservakids.domain.model.AuditEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    List<AuditEvent> findByTenantIdOrderByCreadoEnDesc(Long tenantId, Pageable pageable);

    List<AuditEvent> findByTenantIdAndAccionOrderByCreadoEnDesc(Long tenantId, String accion, Pageable pageable);
}
