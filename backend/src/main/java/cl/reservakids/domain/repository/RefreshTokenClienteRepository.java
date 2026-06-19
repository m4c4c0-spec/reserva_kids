package cl.reservakids.domain.repository;

import cl.reservakids.domain.model.RefreshTokenCliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface RefreshTokenClienteRepository extends JpaRepository<RefreshTokenCliente, Long> {

    Optional<RefreshTokenCliente> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE RefreshTokenCliente rt SET rt.revocado = true WHERE rt.cuentaClienteId = :cuentaId")
    void revocarTodosDeCuenta(@Param("cuentaId") Long cuentaId);

    @Modifying
    @Query("DELETE FROM RefreshTokenCliente rt WHERE rt.revocado = true OR rt.expiraEn <= :ahora")
    int purgarInvalidos(@Param("ahora") OffsetDateTime ahora);
}
