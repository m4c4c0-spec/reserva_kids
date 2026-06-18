package cl.reservakids.domain.repository;

import cl.reservakids.domain.model.CuentaCliente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CuentaClienteRepository extends JpaRepository<CuentaCliente, Long> {
    Optional<CuentaCliente> findByEmail(String email);
    boolean existsByEmail(String email);
}
