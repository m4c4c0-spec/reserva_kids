package cl.reservakids.domain.repository;

import cl.reservakids.domain.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHashAndRevocadoFalse(String tokenHash);

    /** Fix #2 (revisión de código): se busca también revocado — su reuso es evidencia de robo. */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE RefreshToken r SET r.revocado = true WHERE r.usuarioId = :usuarioId")
    void revocarTodosDeUsuario(@Param("usuarioId") Long usuarioId);

    /** Suspensión por el admin (F2): expulsa las sesiones vivas de todos los usuarios del tenant. */
    @Modifying
    @Query("""
            UPDATE RefreshToken r SET r.revocado = true
            WHERE r.usuarioId IN (SELECT u.id FROM Usuario u WHERE u.tenantId = :tenantId)""")
    void revocarTodosDeTenant(@Param("tenantId") Long tenantId);

    /** Mantenimiento: sin esta purga la tabla crece sin límite (cada login/refresh inserta una fila). */
    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.revocado = true OR r.expiraEn < :ahora")
    int purgarInvalidos(@Param("ahora") OffsetDateTime ahora);

    /** Purga física del tenant cerrado (falla 3.3): antes de eliminar sus usuarios. */
    @Modifying
    @Query("""
            DELETE FROM RefreshToken r
            WHERE r.usuarioId IN (SELECT u.id FROM Usuario u WHERE u.tenantId = :tenantId)""")
    int eliminarDeTenant(@Param("tenantId") Long tenantId);
}
