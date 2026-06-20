package cl.reservakids.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * V18: refresh token para administradores de plataforma. Mismo modelo de seguridad que
 * {@link RefreshTokenCliente}: en BD solo vive el SHA-256, rotación en cada uso, revocación
 * masiva ante sospecha de robo y purga periódica.
 */
@Entity
@Table(name = "refresh_token_admin")
@Getter
@Setter
@NoArgsConstructor
public class RefreshTokenAdmin {

    @Id
    private UUID id;

    @Column(name = "administrador_id", nullable = false)
    private Long administradorId;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "expira_en", nullable = false)
    private OffsetDateTime expiraEn;

    @Column(nullable = false)
    private boolean revocado;
}
