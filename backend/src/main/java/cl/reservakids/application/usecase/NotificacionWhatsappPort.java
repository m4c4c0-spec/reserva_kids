package cl.reservakids.application.usecase;

import cl.reservakids.domain.model.Cliente;
import cl.reservakids.domain.model.Reserva;
import cl.reservakids.domain.model.ReservaServicio;
import cl.reservakids.domain.model.Tenant;

import java.util.List;

/**
 * Puerto de salida para WhatsApp. La implementación de infraestructura decide el proveedor:
 * MVP = stub que loguea/simula; luego Meta Cloud API / Twilio sin tocar los casos de uso.
 */
public interface NotificacionWhatsappPort {

    /** Confirmación al cliente de que su cita quedó agendada (tras aprobarse el pago). */
    void confirmacionCita(Tenant tenant, Reserva cita, Cliente cliente, List<ReservaServicio> servicios);
}
