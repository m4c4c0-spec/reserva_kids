package cl.reservakids.domain.repository;

import cl.reservakids.domain.model.RolPersonal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RolPersonalRepository extends JpaRepository<RolPersonal, Long> {

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"permisos"})
    List<RolPersonal> findByTenantIdOrderByNombre(Long tenantId);

    Optional<RolPersonal> findByTenantIdAndNombre(Long tenantId, String nombre);

    @Query("SELECT DISTINCT p.codigo FROM RolPersonal r JOIN r.permisos p WHERE r.id = :rolId")
    List<String> findPermisosByRolId(@Param("rolId") Long rolId);

    boolean existsByTenantIdAndNombre(Long tenantId, String nombre);

    long countByTenantId(Long tenantId);
}
