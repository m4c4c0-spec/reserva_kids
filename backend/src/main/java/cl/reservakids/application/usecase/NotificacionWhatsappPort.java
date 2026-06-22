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

    /** Recordatorio 24h antes: lista los servicios contratados y la hora. */
    void recordatorio(Tenant tenant, Reserva reserva, Cliente cliente, List<ReservaServicio> servicios);

    /** Aviso al dueño de que recibió una nueva solicitud de reserva. */
    void nuevaSolicitudDueno(Tenant tenant, Reserva reserva, Cliente cliente);
}
