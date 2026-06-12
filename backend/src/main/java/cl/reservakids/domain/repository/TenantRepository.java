package cl.reservakids.domain.repository;

import cl.reservakids.domain.model.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface TenantRepository extends JpaRepository<Tenant, Long> {
    Optional<Tenant> findBySlugAndEstado(String slug, String estado);
    boolean existsBySlug(String slug);

    /** Falla 3.3 (5 años): tenants cerrados cuya ventana de gracia ya venció — candidatos a purga. */
    List<Tenant> findByEstadoAndCerradoEnBefore(String estado, OffsetDateTime limite);
}
