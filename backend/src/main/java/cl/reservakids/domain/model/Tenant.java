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
    private String estado = ESTADO_ACTIVO;

    /** Granularidad de los slots de agenda en minutos (ej. cada 30 min). */
    @Column(name = "intervalo_min", nullable = false)
    private Integer intervaloMin = 30;

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

    /** V25: pasarela activa del tenant ('MERCADOPAGO' default para no romper tenants ya configurados). */
    @Column(name = "pasarela_pago", nullable = false)
    private String pasarelaPago = "MERCADOPAGO";

    /** V25: API key (x-api-key) de la cuenta de cobro de Khipu — cifrada en reposo (S1). */
    @Column(name = "khipu_api_key")
    private String khipuApiKey;

    /** V25: Id de cobrador de Khipu — no es secreto, se usa para mostrar/verificar. */
    @Column(name = "khipu_receiver_id")
    private Long khipuReceiverId;

    /** V26: teléfono de contacto del salón (WhatsApp), normalizado a E.164 sin '+'. Público en el mini-sitio. */
    @Column(name = "telefono_contacto")
    private String telefonoContacto;

    // ── V23: suscripción SaaS ──

    @Column(name = "plan_suscripcion", nullable = false)
    private String planSuscripcion = "GRATIS";

    @Column(name = "suscripcion_estado", nullable = false)
    private String suscripcionEstado = "ACTIVO";

    @Column(name = "suscripcion_inicio")
    private OffsetDateTime suscripcionInicio;

    @Column(name = "suscripcion_renovacion")
    private OffsetDateTime suscripcionRenovacion;

    @Column(name = "suscripcion_referencia_externa", length = 120)
    private String suscripcionReferenciaExterna;

    /** V28: color primario de marca blanca (hex, ej. '#b5007d'). */
    @Column(name = "color_primario", nullable = false, length = 7)
    private String colorPrimario = "#b5007d";

    /** V28: titulo personalizado de pagina (pestana del navegador). Nulo = "ReservaKids". */
    @Column(name = "titulo_pagina", length = 120)
    private String tituloPagina;

    /** V28: ID del Meta Pixel para tracking de anuncios. Nulo = sin pixel. */
    @Column(name = "meta_pixel_id", length = 50)
    private String metaPixelId;

    /** V29: politicas de cancelacion del negocio (texto libre). */
    @Column(name = "politicas_cancelacion", columnDefinition = "TEXT")
    private String politicasCancelacion;

    /** V34: Sincronización bidireccional con Google Calendar. */
    @Column(name = "google_calendar_sync_enabled", nullable = false)
    private boolean googleCalendarSyncEnabled = false;

    @Column(name = "google_calendar_id", length = 255)
    private String googleCalendarId;

    @Column(name = "google_calendar_channel_id", length = 255)
    private String googleCalendarChannelId;

    @Column(name = "google_calendar_resource_id", length = 255)
    private String googleCalendarResourceId;

    @Column(name = "google_calendar_channel_expiration")
    private OffsetDateTime googleCalendarChannelExpiration;

    public boolean isActivo() {
        return ESTADO_ACTIVO.equals(estado);
    }

    /** V25: ¿el dueño ya configuró la pasarela que tiene activa? Decide si cotizar genera link de pago. */
    public boolean tienePasarelaConfigurada() {
        if ("KHIPU".equals(pasarelaPago)) {
            return khipuApiKey != null && !khipuApiKey.isBlank();
        }
        return mpAccessToken != null && !mpAccessToken.isBlank();
    }

    /** Cierre a demanda del dueño: la página pública desaparece y los accesos se bloquean. */
    public void cerrar(OffsetDateTime cuando) {
        this.estado = ESTADO_CERRADO;
        this.cerradoEn = cuando;
    }
}
