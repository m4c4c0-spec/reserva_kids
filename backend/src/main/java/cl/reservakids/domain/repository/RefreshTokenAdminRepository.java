package cl.reservakids.domain.repository;

import cl.reservakids.domain.model.RefreshTokenAdmin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface RefreshTokenAdminRepository extends JpaRepository<RefreshTokenAdmin, Long> {

    Optional<RefreshTokenAdmin> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE RefreshTokenAdmin rt SET rt.revocado = true WHERE rt.administradorId = :adminId")
    void revocarTodosDeAdmin(@Param("adminId") Long adminId);

    @Modifying
    @Query("DELETE FROM RefreshTokenAdmin rt WHERE rt.revocado = true OR rt.expiraEn <= :ahora")
    int purgarInvalidos(@Param("ahora") OffsetDateTime ahora);
}
