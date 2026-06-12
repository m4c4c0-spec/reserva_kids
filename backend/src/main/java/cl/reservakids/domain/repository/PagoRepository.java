package cl.reservakids.domain.repository;

import cl.reservakids.domain.model.Pago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PagoRepository extends JpaRepository<Pago, Long> {

    List<Pago> findByReservaIdOrderByFecha(Long reservaId);

    /** Saldo real: solo pagos CONFIRMADOS; las devoluciones restan (libro contable). */
    @Query("""
            SELECT COALESCE(SUM(CASE WHEN p.tipo = 'DEVOLUCION' THEN -p.montoClp ELSE p.montoClp END), 0)
            FROM Pago p WHERE p.reservaId = :reservaId AND p.estado = 'CONFIRMADO'""")
    int totalPagado(@Param("reservaId") Long reservaId);

    /** Fix #7 (revisión de código): totales de una página entera en UNA query (antes: una por reserva). */
    @Query("""
            SELECT p.reservaId, COALESCE(SUM(CASE WHEN p.tipo = 'DEVOLUCION' THEN -p.montoClp ELSE p.montoClp END), 0)
            FROM Pago p WHERE p.reservaId IN :reservaIds AND p.estado = 'CONFIRMADO'
            GROUP BY p.reservaId""")
    List<Object[]> totalesPagadosPorReserva(@Param("reservaIds") Collection<Long> reservaIds);

    // ── Offboarding de tenant (falla 3.3, revisión a 5 años) ──

    /** Export: el libro contable completo del tenant (pago no tiene tenant_id; va vía reserva). */
    @Query("""
            SELECT p FROM Pago p
            WHERE p.reservaId IN (SELECT r.id FROM Reserva r WHERE r.tenantId = :tenantId)
            ORDER BY p.fecha""")
    List<Pago> findDeTenant(@Param("tenantId") Long tenantId);

    /** Purga física del tenant cerrado (primero: pago referencia a reserva y usuario). */
    @Modifying
    @Query("""
            DELETE FROM Pago p
            WHERE p.reservaId IN (SELECT r.id FROM Reserva r WHERE r.tenantId = :tenantId)""")
    int eliminarDeTenant(@Param("tenantId") Long tenantId);
}
