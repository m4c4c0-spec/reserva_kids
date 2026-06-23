package cl.reservakids.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "audit_event")
@Getter
@Setter
@NoArgsConstructor
public class AuditEvent {

    // Actor types
    public static final String ACTOR_DUENO = "DUENO";
    public static final String ACTOR_CLIENTE = "CLIENTE";

    // Acciones del DUENO (panel)
    public static final String RESERVA_COTIZAR = "RESERVA_COTIZAR";
    public static final String RESERVA_CONFIRMAR = "RESERVA_CONFIRMAR";
    public static final String RESERVA_REALIZAR = "RESERVA_REALIZAR";
    public static final String RESERVA_CANCELAR = "RESERVA_CANCELAR";
    public static final String PAGO_REGISTRAR = "PAGO_REGISTRAR";
    public static final String SERVICIO_CREAR = "SERVICIO_CREAR";
    public static final String SERVICIO_EDITAR = "SERVICIO_EDITAR";
    public static final String SERVICIO_DESACTIVAR = "SERVICIO_DESACTIVAR";
    public static final String BLOQUE_CREAR = "BLOQUE_CREAR";
    public static final String BLOQUE_ELIMINAR = "BLOQUE_ELIMINAR";
    public static final String CONFIGURACION_GUARDAR = "CONFIGURACION_GUARDAR";
    public static final String NEGOCIO_CERRAR = "NEGOCIO_CERRAR";

    // Acciones del CLIENTE
    public static final String SOLICITUD_CREAR = "SOLICITUD_CREAR";
    public static final String CITA_AGENDAR = "CITA_AGENDAR";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "actor_id", nullable = false)
    private Long actorId;

    @Column(name = "actor_type", nullable = false)
    private String actorType;

    @Column(nullable = false)
    private String accion;

    @Column(name = "recurso_tipo")
    private String recursoTipo;

    @Column(name = "recurso_id")
    private Long recursoId;

    @Column(length = 1000)
    private String detalle;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "creado_en", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime creadoEn;

    public AuditEvent(Long tenantId, Long actorId, String actorType, String accion,
                      String recursoTipo, Long recursoId, String detalle, String ipAddress) {
        this.tenantId = tenantId;
        this.actorId = actorId;
        this.actorType = actorType;
        this.accion = accion;
        this.recursoTipo = recursoTipo;
        this.recursoId = recursoId;
        this.detalle = detalle;
        this.ipAddress = ipAddress;
    }
}
