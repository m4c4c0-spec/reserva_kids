package cl.reservakids.domain.exception;

/**
 * Falla al comunicarse con la pasarela de pago (Mercado Pago). Unchecked a propósito:
 * el link de pago es opcional, así que {@code ReservaService.cotizar} la captura y guarda
 * la cotización sin link en vez de tumbar la operación. Reemplaza el {@code RuntimeException}
 * genérico que lanzaba el adaptador (Sonar java:S112).
 */
public class PasarelaPagoException extends RuntimeException {

    public PasarelaPagoException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
