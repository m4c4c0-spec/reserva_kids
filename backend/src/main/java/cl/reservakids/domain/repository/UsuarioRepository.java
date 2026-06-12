package cl.reservakids.domain.repository;

import cl.reservakids.domain.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<Usuario> findFirstByTenantIdOrderById(Long tenantId);

    /** Offboarding (falla 3.3): al cerrar el tenant se revocan las sesiones de todos sus usuarios. */
    List<Usuario> findByTenantId(Long tenantId);

    /** Purga física del tenant cerrado (tras eliminar sus refresh tokens). */
    @Modifying
    @Query("DELETE FROM Usuario u WHERE u.tenantId = :tenantId")
    int eliminarDeTenant(@Param("tenantId") Long tenantId);
}
