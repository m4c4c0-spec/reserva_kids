package cl.reservakids.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Administrador de plataforma (V18) — GLOBAL, sin tenant. Gobierna todos los negocios
 * (altas/bajas/suspensión, soporte, métricas). Distinto de {@link Usuario} (dueño de un
 * tenant) y de {@link CuentaCliente} (apoderado). Emite tokens con rol ADMIN sin tenantId.
 */
@Entity
@Table(name = "administrador")
@Getter
@Setter
@NoArgsConstructor
public class Administrador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(nullable = false)
    private String nombre;

    @Column(name = "creado_en", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime creadoEn;

    @Column(name = "oauth_provider", length = 20)
    private String oauthProvider;

    @Column(name = "oauth_provider_id", length = 255)
    private String oauthProviderId;

    public boolean isOauth() {
        return oauthProvider != null;
    }
}
