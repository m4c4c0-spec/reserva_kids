package cl.reservakids.domain.model;

import java.util.Set;

/**
 * Máquina de estados de la reserva (SDLC §4.3):
 * PENDIENTE → COTIZADA → CONFIRMADA → REALIZADA, con CANCELADA desde cualquier estado activo.
 */
public enum EstadoReserva {
    PENDIENTE,
    COTIZADA,
    CONFIRMADA,
    REALIZADA,
    CANCELADA;

    public static final Set<EstadoReserva> ACTIVOS = Set.of(PENDIENTE, COTIZADA, CONFIRMADA);

    public boolean puedeTransicionarA(EstadoReserva destino) {
        return switch (this) {
            case PENDIENTE -> destino == COTIZADA || destino == CANCELADA;
            case COTIZADA -> destino == CONFIRMADA || destino == CANCELADA;
            case CONFIRMADA -> destino == REALIZADA || destino == CANCELADA;
            case REALIZADA, CANCELADA -> false;
        };
    }
}
