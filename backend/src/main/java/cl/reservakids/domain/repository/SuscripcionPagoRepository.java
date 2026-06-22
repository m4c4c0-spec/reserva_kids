package cl.reservakids.domain.repository;

import cl.reservakids.domain.model.SuscripcionPago;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SuscripcionPagoRepository extends JpaRepository<SuscripcionPago, Long> {

    List<SuscripcionPago> findByTenantIdOrderByFechaDesc(Long tenantId);
}
