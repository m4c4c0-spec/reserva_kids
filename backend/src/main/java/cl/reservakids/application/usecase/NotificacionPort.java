package cl.reservakids.application.usecase;

import cl.reservakids.domain.model.Cliente;
import cl.reservakids.domain.model.Reserva;
import cl.reservakids.domain.model.Tenant;

/**
 * Puerto de notificaciones (RF-08). La implementación de infraestructura decide el canal:
 * MVP = log + email simple; luego Resend/SMTP/Twilio sin tocar los casos de uso.
 */
public interface NotificacionPort {

    void nuevaSolicitud(Tenant tenant, Reserva reserva, Cliente cliente);

    /** Link wa.me pre-armado para que el dueño responda al cliente (RF-08). */
    String linkWhatsApp(Cliente cliente, Reserva reserva);
}
