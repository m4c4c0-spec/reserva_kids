package cl.reservakids.application.usecase;

import cl.reservakids.domain.model.Cliente;
import cl.reservakids.domain.model.Reserva;
import cl.reservakids.domain.model.ReservaServicio;
import cl.reservakids.domain.model.Tenant;

import java.util.List;

/**
 * Puerto de salida para WhatsApp. La implementación de infraestructura decide el proveedor:
 * MVP = stub que loguea; luego Meta Cloud API sin tocar los casos de uso.
 */
public interface NotificacionWhatsappPort {

    /** Confirmación automática al cliente de que su reserva quedó agendada (tras aprobarse el pago). */
    void confirmacionReserva(Tenant tenant, Reserva reserva, Cliente cliente, List<ReservaServicio> servicios);

    /**
     * Acuse inmediato al cliente cuando envía la solicitud (PENDIENTE): cierra el vacío de la
     * espera con un mensaje de "recibimos tu solicitud, tu fecha queda reservada por 48 h".
     */
    void solicitudRecibida(Tenant tenant, Reserva reserva, Cliente cliente);

    /** Aviso al dueño de que recibió una nueva solicitud de reserva. */
    void nuevaSolicitudDueno(Tenant tenant, Reserva reserva, Cliente cliente);

    /**
     * Link wa.me pre-armado para que el dueño responda al cliente (RF-08).
     * Incluye el link de pago de Mercado Pago si la reserva está cotizada.
     */
    String linkWhatsApp(Cliente cliente, Reserva reserva);
}
