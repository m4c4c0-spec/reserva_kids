package cl.reservakids.domain.repository;

import cl.reservakids.domain.model.EstadoBloque;
import cl.reservakids.domain.model.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface TenantRepository extends JpaRepository<Tenant, Long> {
    Optional<Tenant> findBySlugAndEstado(String slug, String estado);
    boolean existsBySlug(String slug);

    /** Falla 3.3 (5 años): tenants cerrados cuya ventana de gracia ya venció — candidatos a purga. */
    List<Tenant> findByEstadoAndCerradoEnBefore(String estado, OffsetDateTime limite);

    /**
     * Directorio de cliente: negocios ACTIVOS con al menos un bloque DISPONIBLE de hoy en
     * adelante. DISTINCT porque un negocio con varios bloques saldría repetido.
     */
    @Query("""
            SELECT DISTINCT t FROM Tenant t, BloqueDisponible b
            WHERE b.tenantId = t.id AND t.estado = :activo
              AND b.estado = :disponible AND b.fecha >= :hoy
            ORDER BY t.nombre""")
    List<Tenant> findActivosConDisponibilidad(@Param("activo") String activo,
                                              @Param("disponible") EstadoBloque disponible,
                                              @Param("hoy") LocalDate hoy);
}
