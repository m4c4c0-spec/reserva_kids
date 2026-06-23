package cl.reservakids.domain.repository;

import cl.reservakids.domain.model.Permiso;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PermisoRepository extends JpaRepository<Permiso, Long> {

    Optional<Permiso> findByCodigo(String codigo);

    List<Permiso> findByCategoriaOrderByCodigo(String categoria);

    List<Permiso> findAllByOrderByCategoriaAscCodigoAsc();
}
