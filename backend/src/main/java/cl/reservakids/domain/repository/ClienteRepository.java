package cl.reservakids.domain.repository;

import cl.reservakids.domain.model.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {
    List<Cliente> findByTenantIdOrderByNombre(Long tenantId);
    Optional<Cliente> findByTenantIdAndTelefono(Long tenantId, String telefono);
    Optional<Cliente> findByIdAndTenantId(Long id, Long tenantId);

    /** Ley 21.719 (minimización): candidatos a anonimizar por inactividad prolongada. */
    List<Cliente> findByAnonimizadoEnIsNullAndUltimaActividadEnBefore(OffsetDateTime limite);

    /**
     * Purga física del tenant cerrado (falla 3.3, 5 años) — Ley 21.719: supresión real,
     * no anonimización; borra también el residuo de datos personales de la falla 3.2.
     */
    @Modifying
    @Query("DELETE FROM Cliente c WHERE c.tenantId = :tenantId")
    int eliminarDeTenant(@Param("tenantId") Long tenantId);
}
