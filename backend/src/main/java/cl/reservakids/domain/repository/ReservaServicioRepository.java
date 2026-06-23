package cl.reservakids.domain.repository;

import cl.reservakids.domain.model.ReservaServicio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReservaServicioRepository extends JpaRepository<ReservaServicio, Long> {

    /** Detalle de servicios de una cita (para mostrarla y notificarla). */
    List<ReservaServicio> findByReservaIdOrderById(Long reservaId);
}
