package cl.reservakids.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Un servicio incluido en una cita por hora, con snapshot de nombre/precio/duración al momento
 * de agendar. Editar el catálogo después no altera el detalle ni el total de citas ya tomadas.
 */
@Entity
@Table(name = "reserva_servicio")
@Getter
@Setter
@NoArgsConstructor
public class ReservaServicio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reserva_id", nullable = false)
    private Long reservaId;

    @Column(name = "servicio_id", nullable = false)
    private Long servicioId;

    @Column(nullable = false)
    private String nombre;

    @Column(name = "precio_clp", nullable = false)
    private Integer precioClp;

    @Column(name = "duracion_min", nullable = false)
    private Integer duracionMin;

    public ReservaServicio(Long reservaId, Servicio servicio) {
        this.reservaId = reservaId;
        this.servicioId = servicio.getId();
        this.nombre = servicio.getNombre();
        this.precioClp = servicio.getPrecioClp();
        this.duracionMin = servicio.getDuracionMin();
    }
}
