package cl.reservakids.domain.model;

import java.util.Set;

/**
 * Máquina de estados de la reserva (SDLC §4.3):
 * <ul>
 *   <li>Cumpleaños (cotización manual): PENDIENTE → COTIZADA → CONFIRMADA → REALIZADA.</li>
 *   <li>Cita por hora (pago = agendado): PENDIENTE_PAGO → CONFIRMADA → REALIZADA.</li>
 * </ul>
 * CANCELADA es alcanzable desde cualquier estado activo.
 */
public enum EstadoReserva {
    PENDIENTE,
    COTIZADA,
    /** Cita por hora creada, a la espera de que el webhook de pago la confirme. */
    PENDIENTE_PAGO,
    CONFIRMADA,
    REALIZADA,
    CANCELADA;

    public static final Set<EstadoReserva> ACTIVOS =
            Set.of(PENDIENTE, COTIZADA, PENDIENTE_PAGO, CONFIRMADA);

    public boolean puedeTransicionarA(EstadoReserva destino) {
        return switch (this) {
            case PENDIENTE -> destino == COTIZADA || destino == CANCELADA;
            case COTIZADA -> destino == CONFIRMADA || destino == CANCELADA;
            case PENDIENTE_PAGO -> destino == CONFIRMADA || destino == CANCELADA;
            case CONFIRMADA -> destino == REALIZADA || destino == CANCELADA;
            case REALIZADA, CANCELADA -> false;
        };
    }
}
