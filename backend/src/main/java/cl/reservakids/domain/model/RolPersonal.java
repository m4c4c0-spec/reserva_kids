package cl.reservakids.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "rol_personal")
@Getter
@Setter
@NoArgsConstructor
public class RolPersonal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false, length = 60)
    private String nombre;

    @Column(length = 200)
    private String descripcion;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "rol_personal_permiso",
        joinColumns = @JoinColumn(name = "rol_personal_id"),
        inverseJoinColumns = @JoinColumn(name = "permiso_id")
    )
    private Set<Permiso> permisos = new HashSet<>();

    @Column(name = "creado_en", nullable = false, updatable = false, insertable = false)
    private java.time.OffsetDateTime creadoEn;

    public RolPersonal(Long tenantId, String nombre) {
        this.tenantId = tenantId;
        this.nombre = nombre;
    }
}
