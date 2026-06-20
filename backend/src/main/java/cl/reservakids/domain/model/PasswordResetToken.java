package cl.reservakids.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Falla 1.3 (revisión a 5 años): token de recuperación de contraseña.
 * Mismo modelo de seguridad que {@link RefreshToken}: en BD solo vive el SHA-256,
 * un solo uso y vencimiento corto (30 min por defecto).
 */
@Entity
@Table(name = "password_reset_token")
@Getter
@Setter
@NoArgsConstructor
public class PasswordResetToken {

    @Id
    private UUID id;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "cuenta_cliente_id")
    private Long cuentaClienteId;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "expira_en", nullable = false)
    private OffsetDateTime expiraEn;

    @Column(nullable = false)
    private boolean usado;

    @Column(name = "creado_en", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime creadoEn;

    public boolean vigente(OffsetDateTime ahora) {
        return !usado && expiraEn.isAfter(ahora);
    }
}
