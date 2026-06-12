package cl.reservakids.domain.repository;

import cl.reservakids.domain.model.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/** Falla 1.3 (revisión a 5 años): tokens de recuperación de contraseña. */
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /** Pedir un reset nuevo invalida los anteriores: solo el último enlace sirve. */
    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.usado = true WHERE t.usuarioId = :usuarioId AND t.usado = false")
    int invalidarVigentesDeUsuario(@Param("usuarioId") Long usuarioId);

    /** Mantenimiento diario (mismo job que los refresh): la tabla no crece sin límite. */
    @Modifying
    @Query("DELETE FROM PasswordResetToken t WHERE t.usado = true OR t.expiraEn < :ahora")
    int purgarInvalidos(@Param("ahora") OffsetDateTime ahora);

    /** Purga física del tenant cerrado (falla 3.3): antes de eliminar sus usuarios. */
    @Modifying
    @Query("""
            DELETE FROM PasswordResetToken t
            WHERE t.usuarioId IN (SELECT u.id FROM Usuario u WHERE u.tenantId = :tenantId)""")
    int eliminarDeTenant(@Param("tenantId") Long tenantId);
}
