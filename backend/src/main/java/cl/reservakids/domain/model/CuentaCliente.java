package cl.reservakids.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Cuenta de cliente (apoderado) — GLOBAL, no ligada a un tenant. Inicia sesión para navegar
 * el directorio de negocios con horarios disponibles. Distinta de {@link Usuario} (dueño).
 */
@Entity
@Table(name = "cuenta_cliente")
@Getter
@Setter
@NoArgsConstructor
public class CuentaCliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    private String nombre;

    /** Contacto para confirmación/recordatorio de cita (WhatsApp + correo). E.164 chileno sin '+'. */
    private String telefono;

    /** RUT chileno normalizado (sin puntos, con guión y dígito verificador). Ej: "12345678-5". */
    @Column(length = 12)
    private String rut;

    @Column(name = "oauth_provider", length = 20)
    private String oauthProvider;

    @Column(name = "oauth_provider_id", length = 255)
    private String oauthProviderId;

    public boolean isOauth() {
        return oauthProvider != null;
    }
}
