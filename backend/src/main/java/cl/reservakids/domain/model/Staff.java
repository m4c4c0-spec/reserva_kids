package cl.reservakids.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "staff")
@Getter
@Setter
@NoArgsConstructor
public class Staff {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(nullable = false, unique = true, length = 160)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(length = 20)
    private String telefono;

    /**
     * @deprecated Usar {@link #rolPersonalId} en su lugar. Este campo se mantiene
     * por compatibilidad con datos existentes; los nuevos staff usan el sistema RBAC.
     */
    @Deprecated
    @Column(nullable = false, length = 30)
    private String rol = "ANIMADOR";

    @Column(name = "rol_personal_id")
    private Long rolPersonalId;

    @Column(nullable = false)
    private boolean activo = true;

    @Deprecated
    @Column(name = "whatsapp_recordatorio", nullable = false)
    private boolean whatsappRecordatorio = true;

    @Column(name = "creado_en", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime creadoEn;
}
