package cl.reservakids.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "tenant")
@Getter
@Setter
@NoArgsConstructor
public class Tenant {

    /** Operación normal: página pública visible, panel accesible. */
    public static final String ESTADO_ACTIVO = "ACTIVO";
    /** Falla #10 (2 años) / morosidad futura: acceso bloqueado, datos intactos — reversible por SQL. */
    public static final String ESTADO_SUSPENDIDO = "SUSPENDIDO";
    /** Falla 3.3 (5 años): el dueño cerró el negocio; purga física tras la ventana de gracia. */
    public static final String ESTADO_CERRADO = "CERRADO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String plan = "BASICO";

    @Column(nullable = false)
    private String estado = "ACTIVO";

    @Column(name = "creado_en", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime creadoEn;

    /** Base del plazo de purga física (falla 3.3) y de la reapertura por arrepentimiento. */
    @Column(name = "cerrado_en")
    private OffsetDateTime cerradoEn;

    /** S1: Access Token de Mercado Pago — cifrado en reposo (ver CredentialCipher). */
    @Column(name = "mp_access_token")
    private String mpAccessToken;

    /** S3: secreto de firma del webhook de MP (panel de MP) — cifrado en reposo. */
    @Column(name = "mp_webhook_secret")
    private String mpWebhookSecret;

    public boolean isActivo() {
        return ESTADO_ACTIVO.equals(estado);
    }

    /** Cierre a demanda del dueño: la página pública desaparece y los accesos se bloquean. */
    public void cerrar(OffsetDateTime cuando) {
        this.estado = ESTADO_CERRADO;
        this.cerradoEn = cuando;
    }
}
