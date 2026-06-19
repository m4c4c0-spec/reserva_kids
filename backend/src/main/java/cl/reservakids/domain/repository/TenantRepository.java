package cl.reservakids.domain.repository;

import cl.reservakids.domain.model.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface TenantRepository extends JpaRepository<Tenant, Long> {
    Optional<Tenant> findBySlugAndEstado(String slug, String estado);
    boolean existsBySlug(String slug);

    /** Falla 3.3 (5 años): tenants cerrados cuya ventana de gracia ya venció — candidatos a purga. */
    List<Tenant> findByEstadoAndCerradoEnBefore(String estado, OffsetDateTime limite);

    /**
     * Directorio de cliente: negocios ACTIVOS agendables, es decir con horario de atención
     * configurado y al menos un servicio activo. (Reemplaza el viejo filtro por bloques
     * pre-creados — el agendamiento ahora es por hora calculada, no por bloques.)
     */
    @Query("""
            SELECT t FROM Tenant t
            WHERE t.estado = :activo
              AND EXISTS (SELECT 1 FROM HorarioAtencion h WHERE h.tenantId = t.id)
              AND EXISTS (SELECT 1 FROM Servicio s WHERE s.tenantId = t.id AND s.activo = true)
            ORDER BY t.nombre""")
    List<Tenant> findAgendables(@Param("activo") String activo);
}
