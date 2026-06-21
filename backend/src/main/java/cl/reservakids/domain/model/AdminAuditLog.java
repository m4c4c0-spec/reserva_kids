package cl.reservakids.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * V19: bitácora append-only de acciones del administrador de plataforma (F5).
 * Registra quién (administradorId), qué (accion), sobre qué negocio (tenantId, opcional) y cuándo.
 */
@Entity
@Table(name = "admin_audit_log")
@Getter
@Setter
@NoArgsConstructor
public class AdminAuditLog {

    public static final String SUSPENDER_NEGOCIO = "SUSPENDER_NEGOCIO";
    public static final String REACTIVAR_NEGOCIO = "REACTIVAR_NEGOCIO";
    public static final String CREAR_ADMIN = "CREAR_ADMIN";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "administrador_id", nullable = false)
    private Long administradorId;

    @Column(nullable = false)
    private String accion;

    @Column(name = "tenant_id")
    private Long tenantId;

    private String detalle;

    @Column(name = "creado_en", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime creadoEn;

    public AdminAuditLog(Long administradorId, String accion, Long tenantId, String detalle) {
        this.administradorId = administradorId;
        this.accion = accion;
        this.tenantId = tenantId;
        this.detalle = detalle;
    }
}
