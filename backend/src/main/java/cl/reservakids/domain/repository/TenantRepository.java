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

    /** Métricas de plataforma (F4): nº de negocios en un estado dado. */
    long countByEstado(String estado);

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

    /**
     * Consola de admin (F2): todos los negocios con su nº de reservas, filtrable por estado y
     * texto (nombre/slug). Un único query agregado (LEFT JOIN + GROUP BY) evita el N+1 de contar
     * reservas por tenant. Proyección {@link NegocioAdminView}.
     */
    @Query("""
            SELECT t.id AS id, t.slug AS slug, t.nombre AS nombre, t.plan AS plan,
                   t.estado AS estado, t.creadoEn AS creadoEn, COUNT(r.id) AS reservas
            FROM Tenant t LEFT JOIN Reserva r ON r.tenantId = t.id
            WHERE (:estado IS NULL OR t.estado = :estado)
              AND (:patron IS NULL
                   OR LOWER(t.nombre) LIKE :patron
                   OR LOWER(t.slug)   LIKE :patron)
            GROUP BY t.id, t.slug, t.nombre, t.plan, t.estado, t.creadoEn
            ORDER BY t.creadoEn DESC""")
    List<NegocioAdminView> listarParaAdmin(@Param("estado") String estado, @Param("patron") String patron);

    /** V34: Buscar tenant por canal de Google Calendar watch. */
    Optional<Tenant> findByGoogleCalendarChannelIdAndGoogleCalendarResourceId(String channelId, String resourceId);
}
