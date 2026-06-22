package cl.reservakids.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Datos del adulto contratante. Por política de privacidad (Ley 21.719, riesgo #9 del SDLC)
 * NO se almacenan datos de menores. El titular consiente el tratamiento al enviar la
 * solicitud pública ({@code consentimientoEn}) y puede ejercer el derecho de supresión
 * ({@link #anonimizar}); la retención se limita por inactividad ({@code ultimaActividadEn}).
 */
@Entity
@Table(name = "cliente")
@Getter
@Setter
@NoArgsConstructor
public class Cliente {

    public static final String NOMBRE_ANONIMO = "[eliminado]";

    /**
     * Falla #9 (revisión a 2 años): el cliente se deduplica por (tenant, teléfono) y el link
     * wa.me requiere código de país — "+56 9 1234 5678", "56912345678" y "912345678" creaban
     * tres clientes distintos y links rotos. Normaliza a E.164 chileno sin '+' (56XXXXXXXXX).
     */
    public static String normalizarTelefono(String raw) {
        if (raw == null) {
            return null;
        }
        String digitos = raw.replaceAll("\\D", "");
        if (digitos.startsWith("00")) {
            digitos = digitos.substring(2); // prefijo internacional 00 (fix #8: 0056... → 56...)
        }
        if (digitos.length() == 9 && digitos.startsWith("9")) {
            return "56" + digitos; // celular escrito sin código de país (lo habitual en Chile)
        }
        return digitos;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private String telefono;

    @Column(length = 12)
    private String rut;

    private String email;

    /** Prueba de consentimiento (Ley 21.719): cuándo aceptó el tratamiento de sus datos. */
    @Column(name = "consentimiento_en")
    private OffsetDateTime consentimientoEn;

    /** Base del plazo de retención: se renueva con cada solicitud del titular. */
    @Column(name = "ultima_actividad_en")
    private OffsetDateTime ultimaActividadEn;

    @Column(name = "anonimizado_en")
    private OffsetDateTime anonimizadoEn;

    public boolean isAnonimizado() {
        return anonimizadoEn != null;
    }

    /**
     * Derecho de supresión (Ley 21.719): borra los datos personales conservando la fila
     * — las reservas históricas la referencian (integridad y números del negocio intactos).
     * El teléfono queda con un placeholder único para no chocar con clientes reales.
     */
    public void anonimizar(OffsetDateTime cuando) {
        this.nombre = NOMBRE_ANONIMO;
        this.telefono = "anon-" + id;
        this.rut = null;
        this.email = null;
        this.consentimientoEn = null;
        this.anonimizadoEn = cuando;
    }
}
