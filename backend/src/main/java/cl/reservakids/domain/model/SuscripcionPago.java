package cl.reservakids.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "suscripcion_pago")
@Getter
@Setter
@NoArgsConstructor
public class SuscripcionPago {

    public static final String ESTADO_PENDIENTE = "PENDIENTE";
    public static final String ESTADO_APROBADO = "APROBADO";
    public static final String ESTADO_RECHAZADO = "RECHAZADO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false)
    private String plan;

    @Column(name = "monto_clp", nullable = false)
    private Integer montoClp;

    @Column(nullable = false)
    private String periodo;

    @Column(nullable = false)
    private String estado;

    @Column(name = "referencia_externa", length = 120)
    private String referenciaExterna;

    @Column(nullable = false, updatable = false, insertable = false)
    private OffsetDateTime fecha;

    public SuscripcionPago(Long tenantId, String plan, Integer montoClp, String periodo) {
        this.tenantId = tenantId;
        this.plan = plan;
        this.montoClp = montoClp;
        this.periodo = periodo;
        this.estado = ESTADO_PENDIENTE;
    }
}
