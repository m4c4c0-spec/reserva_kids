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

    /** Job de expiración (RF-05): solicitudes pendientes más antiguas que el límite. */
    List<Reserva> findByEstadoAndCreadaEnBefore(EstadoReserva estado, OffsetDateTime limite);

    /**
     * Ocupación del agendamiento por hora: citas de un negocio que arrancan dentro del rango
     * y en algún estado dado (PENDIENTE_PAGO/CONFIRMADA). Base para restar horas ya tomadas.
     */
    @Query("""
            SELECT r FROM Reserva r
            WHERE r.tenantId = :tenantId AND r.inicio >= :desde AND r.inicio < :hasta
              AND r.estado IN :estados""")
    List<Reserva> findCitasEntre(@Param("tenantId") Long tenantId,
                                 @Param("desde") OffsetDateTime desde,
                                 @Param("hasta") OffsetDateTime hasta,
                                 @Param("estados") Collection<EstadoReserva> estados);

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
}
