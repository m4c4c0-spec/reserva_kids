package cl.reservakids.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * V16: refresh token para cuentas de cliente (apoderados). Mismo modelo de seguridad
 * que {@link RefreshToken}: en BD solo vive el SHA-256, rotación en cada uso,
 * revocación masiva ante sospecha de robo y purga periódica.
 */
@Entity
@Table(name = "refresh_token_cliente")
@Getter
@Setter
@NoArgsConstructor
public class RefreshTokenCliente {

    @Id
    private UUID id;

    @Column(name = "cuenta_cliente_id", nullable = false)
    private Long cuentaClienteId;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "expira_en", nullable = false)
    private OffsetDateTime expiraEn;

    @Column(nullable = false)
    private boolean revocado;
}
