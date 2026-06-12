package cl.reservakids.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "pago")
@Getter
@Setter
@NoArgsConstructor
public class Pago {

    public static final String TIPO_ABONO = "ABONO";
    public static final String TIPO_DEVOLUCION = "DEVOLUCION";
    public static final String ESTADO_CONFIRMADO = "CONFIRMADO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reserva_id", nullable = false)
    private Long reservaId;

    /** Siempre positivo; el signo lo da el tipo (ABONO suma, DEVOLUCION resta). */
    @Column(name = "monto_clp", nullable = false)
    private Integer montoClp;

    /** ABONO | DEVOLUCION — libro contable: una devolución es una fila nueva, nunca un UPDATE. */
    @Column(nullable = false)
    private String tipo = TIPO_ABONO;

    /** PENDIENTE | CONFIRMADO | RECHAZADO. Manual nace CONFIRMADO; la pasarela (P1) usará PENDIENTE. */
    @Column(nullable = false)
    private String estado = ESTADO_CONFIRMADO;

    /** TRANSFERENCIA, EFECTIVO, etc. MVP: registro manual de señas (sin pasarela). */
    @Column(nullable = false)
    private String medio;

    @Column(name = "comprobante_url")
    private String comprobanteUrl;

    /** Id del pago en la pasarela (P1, Mercado Pago) — único en BD: idempotencia de webhooks. */
    @Column(name = "referencia_externa")
    private String referenciaExterna;

    /** Auditoría mínima: usuario del panel que registró el pago (NULL = sistema/pasarela). */
    @Column(name = "registrado_por")
    private Long registradoPor;

    @Column(nullable = false, updatable = false, insertable = false)
    private OffsetDateTime fecha;
}
