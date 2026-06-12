package cl.reservakids.domain.repository;

import cl.reservakids.domain.model.Servicio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ServicioRepository extends JpaRepository<Servicio, Long> {
    List<Servicio> findByTenantIdOrderByNombre(Long tenantId);
    List<Servicio> findByTenantIdAndActivoTrueOrderByNombre(Long tenantId);
    Optional<Servicio> findByIdAndTenantId(Long id, Long tenantId);

    /** Purga física del tenant cerrado (falla 3.3, revisión a 5 años). */
    @Modifying
    @Query("DELETE FROM Servicio s WHERE s.tenantId = :tenantId")
    int eliminarDeTenant(@Param("tenantId") Long tenantId);
}
