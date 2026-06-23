package cl.reservakids.domain.repository;

import cl.reservakids.domain.model.Invitado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InvitadoRepository extends JpaRepository<Invitado, Long> {

    List<Invitado> findByReservaIdOrderByCreadoEn(Long reservaId);

    Optional<Invitado> findByToken(String token);

    long countByReservaIdAndEstado(Long reservaId, String estado);
}
