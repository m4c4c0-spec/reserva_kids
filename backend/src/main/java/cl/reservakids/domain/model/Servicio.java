package cl.reservakids.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "servicio")
@Getter
@Setter
@NoArgsConstructor
public class Servicio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false)
    private String nombre;

    private String descripcion;

    @Column(name = "precio_clp", nullable = false)
    private Integer precioClp;

    @Column(name = "duracion_min")
    private Integer duracionMin;

    private Integer capacidad;

    @Column(nullable = false)
    private boolean activo = true;
}
