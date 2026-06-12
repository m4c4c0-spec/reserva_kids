package cl.reservakids.domain.exception;

/** RNF-05: el bloque ya fue tomado por otra reserva → HTTP 409. */
public class ConflictoBloqueException extends RuntimeException {
    public ConflictoBloqueException(String mensaje) {
        super(mensaje);
    }
}
