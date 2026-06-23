package cl.reservakids.domain.repository;

import cl.reservakids.domain.model.Reserva;
import cl.reservakids.domain.model.Tenant;

/**
 * Puerto de salida para integraciones con pasarelas de pago (Mercado Pago, Stripe, etc).
 */
public interface PasarelaPagoPort {

    /**
     * Genera la preferencia/intención de pago en la pasarela externa usando las credenciales
     * específicas del tenant y retorna los datos necesarios para redirigir al cliente.
     */
    PreferenciaPagoResponse crearPreferenciaDePago(Reserva reserva, Tenant tenant);

    record PreferenciaPagoResponse(String preferenceId, String initPoint) {}
}
