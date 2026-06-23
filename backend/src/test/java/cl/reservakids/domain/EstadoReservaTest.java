package cl.reservakids.domain;

import cl.reservakids.domain.exception.TransicionInvalidaException;
import cl.reservakids.domain.model.EstadoReserva;
import cl.reservakids.domain.model.Reserva;
import org.junit.jupiter.api.Test;

import static cl.reservakids.domain.model.EstadoReserva.*;
import static org.junit.jupiter.api.Assertions.*;

/** Máquina de estados de la reserva (SDLC §4.3) como especificación ejecutable. */
class EstadoReservaTest {

    @Test
    void flujoFelizCompleto() {
        Reserva reserva = new Reserva();
        assertEquals(PENDIENTE, reserva.getEstado());
        reserva.transicionarA(COTIZADA);
        reserva.transicionarA(CONFIRMADA);
        reserva.transicionarA(REALIZADA);
        assertEquals(REALIZADA, reserva.getEstado());
    }

    @Test
    void flujoCitaPorHora() {
        Reserva reserva = new Reserva();
        reserva.setEstado(PENDIENTE_PAGO);
        reserva.transicionarA(CONFIRMADA); // el webhook de pago confirma
        reserva.transicionarA(REALIZADA);
        assertEquals(REALIZADA, reserva.getEstado());
    }

    @Test
    void puedeCancelarseDesdeCualquierEstadoActivo() {
        for (EstadoReserva activo : EstadoReserva.ACTIVOS) {
            assertTrue(activo.puedeTransicionarA(CANCELADA), activo + " debe poder cancelarse");
        }
    }

    @Test
    void noPuedeSaltarseEstados() {
        assertFalse(PENDIENTE.puedeTransicionarA(CONFIRMADA));
        assertFalse(PENDIENTE.puedeTransicionarA(REALIZADA));
        assertFalse(COTIZADA.puedeTransicionarA(REALIZADA));
    }

    @Test
    void estadosFinalesSonInmutables() {
        for (EstadoReserva destino : EstadoReserva.values()) {
            assertFalse(REALIZADA.puedeTransicionarA(destino));
            assertFalse(CANCELADA.puedeTransicionarA(destino));
        }
    }

    @Test
    void transicionInvalidaLanzaExcepcion() {
        Reserva reserva = new Reserva();
        assertThrows(TransicionInvalidaException.class,
                () -> reserva.transicionarA(REALIZADA));
    }
}
