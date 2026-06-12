package cl.reservakids.domain.repository;

import cl.reservakids.domain.model.BloqueDisponible;
import cl.reservakids.domain.model.EstadoBloque;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BloqueDisponibleRepository extends JpaRepository<BloqueDisponible, Long> {

    List<BloqueDisponible> findByTenantIdAndFechaBetweenOrderByFechaAscHoraInicioAsc(
            Long tenantId, LocalDate desde, LocalDate hasta);

    List<BloqueDisponible> findByTenantIdAndEstadoAndFechaBetweenOrderByFechaAscHoraInicioAsc(
            Long tenantId, EstadoBloque estado, LocalDate desde, LocalDate hasta);

    Optional<BloqueDisponible> findByIdAndTenantId(Long id, Long tenantId);

    /**
     * RNF-05: toma atómica del bloque — UPDATE condicionado al estado actual.
     * Si devuelve 0 filas, el bloque ya no estaba en el estado esperado (conflicto).
     */
    @Modifying
    @Query("""
            UPDATE BloqueDisponible b SET b.estado = :nuevo
            WHERE b.id = :id AND b.tenantId = :tenantId AND b.estado = :esperado
            """)
    int transicionarEstado(@Param("id") Long id,
                           @Param("tenantId") Long tenantId,
                           @Param("esperado") EstadoBloque esperado,
                           @Param("nuevo") EstadoBloque nuevo);

    /**
     * Falla #11 (revisión a 2 años): bloques pasados aún en un estado dado y sin ninguna
     * reserva que los referencie son ruido histórico — el job semanal los elimina.
     * (Los bloques con reservas, aun canceladas, se conservan por la FK y la trazabilidad.)
     */
    @Modifying
    @Query("""
            DELETE FROM BloqueDisponible b
            WHERE b.fecha < :fecha AND b.estado = :estado
            AND NOT EXISTS (SELECT r FROM Reserva r WHERE r.bloqueId = b.id)
            """)
    int eliminarPasadosSinReserva(@Param("fecha") LocalDate fecha,
                                  @Param("estado") EstadoBloque estado);

    /** Export de datos del tenant (offboarding, falla 3.3): calendario completo. */
    List<BloqueDisponible> findByTenantIdOrderByFechaAscHoraInicioAsc(Long tenantId);

    /** Purga física del tenant cerrado (tras eliminar sus reservas). */
    @Modifying
    @Query("DELETE FROM BloqueDisponible b WHERE b.tenantId = :tenantId")
    int eliminarDeTenant(@Param("tenantId") Long tenantId);
}
