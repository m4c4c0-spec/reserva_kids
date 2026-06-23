package cl.reservakids.domain.repository;

import cl.reservakids.domain.model.Staff;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StaffRepository extends JpaRepository<Staff, Long> {

    List<Staff> findByTenantIdAndActivoTrueOrderByNombre(Long tenantId);

    List<Staff> findByTenantIdOrderByNombre(Long tenantId);

    Optional<Staff> findByEmail(String email);

    Optional<Staff> findByIdAndTenantId(Long id, Long tenantId);

    long countByTenantId(Long tenantId);

    long countByRolPersonalId(Long rolPersonalId);

    List<Staff> findByTenantIdAndRolPersonalId(Long tenantId, Long rolPersonalId);

    /** Staff activos con WhatsApp habilitado, para recordatorios automáticos. */
    List<Staff> findByTenantIdAndActivoTrueAndWhatsappRecordatorioTrue(Long tenantId);
}
