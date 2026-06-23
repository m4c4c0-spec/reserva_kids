package cl.reservakids.domain.repository;

import cl.reservakids.domain.model.EstadoReserva;
import cl.reservakids.domain.model.Reserva;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReservaRepository extends JpaRepository<Reserva, Long> {
    Page<Reserva> findByTenantIdOrderByCreadaEnDesc(Long tenantId, Pageable pageable);
    Page<Reserva> findByTenantIdAndEstadoOrderByCreadaEnDesc(Long tenantId, EstadoReserva estado, Pageable pageable);
    Optional<Reserva> findByIdAndTenantId(Long id, Long tenantId);

    /**
     * Búsqueda del panel del dueño: coincide por #reserva exacto (cuando el término es
     * numérico, {@code reservaId}) o por nombre de cliente (subconsulta sobre Cliente,
     * tenant-scoped). El patrón se arma en Java (LIKE directo, sin CONCAT) para que el
     * bind se tipe como texto y no como bytea con términos vacíos. Mismo orden que el
     * listado normal; los totales/clientes se cargan después (anti-N+1 en el servicio).
     */
    @Query("""
            SELECT r FROM Reserva r
            WHERE r.tenantId = :tenantId
              AND (r.id = :reservaId
                   OR r.clienteId IN (SELECT c.id FROM Cliente c
                                      WHERE c.tenantId = :tenantId AND LOWER(c.nombre) LIKE :patron))
            ORDER BY r.creadaEn DESC""")
    Page<Reserva> buscar(@Param("tenantId") Long tenantId, @Param("reservaId") Long reservaId,
                         @Param("patron") String patron, Pageable pageable);

    @Query("""
            SELECT r FROM Reserva r
            WHERE r.tenantId = :tenantId AND r.estado = :estado
              AND (r.id = :reservaId
                   OR r.clienteId IN (SELECT c.id FROM Cliente c
                                      WHERE c.tenantId = :tenantId AND LOWER(c.nombre) LIKE :patron))
            ORDER BY r.creadaEn DESC""")
    Page<Reserva> buscarPorEstado(@Param("tenantId") Long tenantId, @Param("estado") EstadoReserva estado,
                                  @Param("reservaId") Long reservaId, @Param("patron") String patron,
                                  Pageable pageable);

    /** Consola de admin (F2): nº de reservas de un negocio (detalle de un tenant). */
    long countByTenantId(Long tenantId);

    /** Métricas de plataforma (F4): nº de reservas en un conjunto de estados (p. ej. ACTIVOS). */
    long countByEstadoIn(Collection<EstadoReserva> estados);

    /** Job de expiración (RF-05): solicitudes pendientes más antiguas que el límite. */
    List<Reserva> findByEstadoAndCreadaEnBefore(EstadoReserva estado, OffsetDateTime limite);

    /**
     * Ocupación del agendamiento por hora: citas de un negocio cuya franja [inicio, fin)
     * se solapa con [desde, hasta) y en algún estado dado (PENDIENTE_PAGO/CONFIRMADA).
     * Base para restar horas ya tomadas.
     */
    @Query("""
            SELECT r FROM Reserva r
            WHERE r.tenantId = :tenantId AND r.inicio < :hasta AND r.fin > :desde
              AND r.estado IN :estados""")
    List<Reserva> findCitasEntre(@Param("tenantId") Long tenantId,
                                 @Param("desde") OffsetDateTime desde,
                                 @Param("hasta") OffsetDateTime hasta,
                                 @Param("estados") Collection<EstadoReserva> estados);

    /**
     * V15: advisory lock por (tenant_id, fecha) para serializar agendamientos de un
     * mismo negocio en un mismo día. Libera automáticamente al COMMIT/ROLLBACK.
     * <p>
     * No usa {@code @Modifying}: {@code pg_advisory_xact_lock} retorna {@code void}
     * (una fila con valor nulo), y Hibernate lanzaba "Se retornó un resultado cuando
     * no se esperaba ninguno" (SQLState 0100E). Como SELECT simple Spring Data ejecuta
     * la función, adquiere el lock y descarta el valor de retorno.
     */
    @Query(value = "SELECT pg_advisory_xact_lock(hashtext(cast(:tenantId AS text) || '-' || cast(:fecha AS text)))", nativeQuery = true)
    Object bloquearDia(@Param("tenantId") Long tenantId, @Param("fecha") LocalDate fecha);

    /** Expiración de citas no pagadas (PENDIENTE_PAGO) creadas antes del límite — liberan la hora. */
    List<Reserva> findByEstadoAndInicioIsNotNullAndCreadaEnBefore(EstadoReserva estado, OffsetDateTime limite);

    /** Expiración de cotizaciones sin respuesta (falla #1, revisión a 2 años). */
    List<Reserva> findByEstadoAndCotizadaEnBefore(EstadoReserva estado, OffsetDateTime limite);

    /** Ley 21.719: un cliente con reservas activas no puede anonimizarse todavía. */
    boolean existsByClienteIdAndEstadoIn(Long clienteId, Collection<EstadoReserva> estados);

    /**
     * Falla #2 (revisión a 2 años): reservas en un estado dado cuyo bloque ya pasó —
     * el barrido diario las cierra (CONFIRMADA → REALIZADA).
     */
    @Query("""
            SELECT r FROM Reserva r, BloqueDisponible b
            WHERE b.id = r.bloqueId AND r.estado = :estado AND b.fecha < :fecha
            """)
    List<Reserva> findByEstadoConBloqueAnterior(@Param("estado") EstadoReserva estado,
                                                @Param("fecha") LocalDate fecha);

    /**
     * Falla 3.2 (revisión a 5 años, Ley 21.719): los comentarios sobrevivían a
     * {@code Cliente.anonimizar()} — la supresión quedaba a medias. Se reemplazan enteros
     * (no regex sobre marcadores: lo que el apoderado escribió también es dato personal).
     * Seguro como bulk update: el cliente sin reservas activas solo tiene reservas en estado
     * terminal, que nadie más edita; {@code version + 1} a mano porque los UPDATE JPQL no
     * pasan por @Version. Idempotente: la segunda pasada no matchea ninguna fila (falla 4.4).
     */
    @Modifying
    @Query("""
            UPDATE Reserva r SET r.comentarios = :placeholder, r.version = r.version + 1
            WHERE r.clienteId = :clienteId AND r.comentarios IS NOT NULL
            AND r.comentarios <> :placeholder""")
    int anonimizarComentariosDeCliente(@Param("clienteId") Long clienteId,
                                       @Param("placeholder") String placeholder);

    // ── Offboarding de tenant (falla 3.3, revisión a 5 años) ──

    /** Export de datos del tenant: todas sus reservas, sin paginar. */
    List<Reserva> findByTenantIdOrderByCreadaEnDesc(Long tenantId);

    /** Cierre: PENDIENTE/COTIZADA se cancelan (nadie las atenderá). */
    List<Reserva> findByTenantIdAndEstadoIn(Long tenantId, Collection<EstadoReserva> estados);

    /** Cierre: las CONFIRMADA (con seña de por medio) exigen resolución manual previa. */
    boolean existsByTenantIdAndEstado(Long tenantId, EstadoReserva estado);

    /** Purga física del tenant cerrado (tras eliminar sus pagos). */
    @Modifying
    @Query("DELETE FROM Reserva r WHERE r.tenantId = :tenantId")
    int eliminarDeTenant(@Param("tenantId") Long tenantId);

    // ── Historial de reservas del cliente autenticado (Sprint 2 §2.2) ──

    /** Citas agendadas por una cuenta de cliente (apoderado), todas las que hizo en cualquier negocio. */
    Page<Reserva> findByCuentaClienteIdOrderByCreadaEnDesc(Long cuentaClienteId, Pageable pageable);

    /** Ídem con filtro por estado (pestaña "Pendientes", "Confirmadas", etc.). */
    Page<Reserva> findByCuentaClienteIdAndEstadoOrderByCreadaEnDesc(
            Long cuentaClienteId, EstadoReserva estado, Pageable pageable);

    /**
     * Recordatorio 24h: citas por hora confirmadas cuyo inicio cae en [desde, hasta).
     * Excluye las que ya tienen recordatorio enviado en la última hora (evita duplicados
     * si el job corre más de una vez en la ventana).
     */
    @Query("""
            SELECT r FROM Reserva r
            WHERE r.estado = 'CONFIRMADA' AND r.inicio IS NOT NULL
              AND r.inicio >= :desde AND r.inicio < :hasta
            ORDER BY r.inicio""")
    List<Reserva> findCitasConfirmadasProximas(@Param("desde") OffsetDateTime desde,
                                               @Param("hasta") OffsetDateTime hasta);

    /**
     * Recordatorio 24h: cumpleañeros confirmados cuyo bloque cae en la fecha indicada.
     */
    @Query("""
            SELECT r FROM Reserva r JOIN BloqueDisponible b ON b.id = r.bloqueId
            WHERE r.estado = 'CONFIRMADA' AND r.bloqueId IS NOT NULL
              AND b.fecha = :fecha
            ORDER BY b.horaInicio""")
    List<Reserva> findCumpleanosConfirmadosEnFecha(@Param("fecha") LocalDate fecha);

    // ── Métricas del panel (dashboard) ──

    /** Ingresos por señas de un tenant en un rango de meses. */
    @Query("""
            SELECT FUNCTION('TO_CHAR', r.creadaEn, 'YYYY-MM'), COALESCE(SUM(r.seniaClp), 0)
            FROM Reserva r
            WHERE r.tenantId = :tenantId AND r.estado IN ('CONFIRMADA', 'REALIZADA')
              AND r.creadaEn >= :desde
            GROUP BY FUNCTION('TO_CHAR', r.creadaEn, 'YYYY-MM')
            ORDER BY FUNCTION('TO_CHAR', r.creadaEn, 'YYYY-MM')""")
    List<Object[]> ingresosMensuales(@Param("tenantId") Long tenantId, @Param("desde") OffsetDateTime desde);

    /** Conteo de reservas por servicio (top N). */
    @Query("""
            SELECT s.nombre, COUNT(r)
            FROM Reserva r JOIN Servicio s ON s.id = r.servicioId
            WHERE r.tenantId = :tenantId AND r.estado IN ('CONFIRMADA', 'REALIZADA')
              AND s.activo = true
            GROUP BY s.nombre
            ORDER BY COUNT(r) DESC""")
    List<Object[]> serviciosMasVendidos(@Param("tenantId") Long tenantId);

    /** Tasa de ocupación mensual: días con al menos una reserva / días totales. */
    @Query("""
            SELECT COUNT(DISTINCT FUNCTION('TO_CHAR', r.creadaEn, 'YYYY-MM-DD'))
            FROM Reserva r
            WHERE r.tenantId = :tenantId AND r.estado IN ('CONFIRMADA', 'REALIZADA')
              AND r.creadaEn >= :desde""")
    long diasConReservas(@Param("tenantId") Long tenantId, @Param("desde") OffsetDateTime desde);
}
