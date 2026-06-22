package cl.reservakids.domain.repository;

import cl.reservakids.domain.model.AuthEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuthEventRepository extends JpaRepository<AuthEvent, Long> {

    List<AuthEvent> findAllByOrderByCreadoEnDesc(Pageable pageable);

    List<AuthEvent> findByEmailHashOrderByCreadoEnDesc(String emailHash, Pageable pageable);
}
