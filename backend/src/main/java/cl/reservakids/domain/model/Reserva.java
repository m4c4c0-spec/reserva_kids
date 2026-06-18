package cl.reservakids.domain.model;

import cl.reservakids.domain.exception.TransicionInvalidaException;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "reserva")
@Getter
@Setter
@NoArgsConstructor
public class Reserva {

    /**
     * Falla 3.2 (revisión a 5 años, Ley 21.719): los comentarios son texto libre con datos
     * personales ("[Contacto: ...]", motivos de cancelación, lo que escribió el apoderado).
     * Al anonimizar al cliente se reemplazan ENTEROS por este placeholder — regla de diseño:
     * ningún dato personal nuevo en campos de texto libre; si se necesita, columna propia
     * que la anonimización conozca.
     */
    public static final String COMENTARIOS_ANONIMIZADOS = "[anonimizado]";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Fix #3 (revisión de código): optimistic locking. Sin esto, el job de expiración y el
     * panel podían pisarse (lost update): el dueño confirmaba mientras el job cancelaba la
     * misma COTIZADA vencida → reserva CANCELADA con bloque CONFIRMADO huérfano. El perdedor
     * de la carrera ahora recibe OptimisticLockException (409 en la API; el job reintenta).
     */
    @Version
    private Long version;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "cliente_id", nullable = false)
    private Long clienteId;

    @Column(name = "servicio_id", nullable = false)
    private Long servicioId;

    @Column(name = "bloque_id", nullable = false)
    private Long bloqueId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoReserva estado = EstadoReserva.PENDIENTE;

    @Column(name = "num_ninos")
    private Integer numNinos;

    private String comuna;

    private String comentarios;

    @Column(name = "total_clp")
    private Integer totalClp;

    @Column(name = "senia_clp")
    private Integer seniaClp;

    @Column(name = "creada_en", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime creadaEn;

    /** Cuándo el dueño envió la cotización — base de la expiración de COTIZADA sin respuesta. */
    @Column(name = "cotizada_en")
    private OffsetDateTime cotizadaEn;

    @Column(name = "mp_preference_id")
    private String mpPreferenceId;

    @Column(name = "mp_init_point")
    private String mpInitPoint;

    /** Regla de dominio: solo transiciones válidas de la máquina de estados (§4.3). */
    public void transicionarA(EstadoReserva destino) {
        if (!estado.puedeTransicionarA(destino)) {
            throw new TransicionInvalidaException(
                    "Transición inválida de reserva: %s → %s".formatted(estado, destino));
        }
        this.estado = destino;
    }
}
