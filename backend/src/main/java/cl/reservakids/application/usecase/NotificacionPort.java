package cl.reservakids.application.usecase;

import cl.reservakids.domain.model.Cliente;
import cl.reservakids.domain.model.CuentaCliente;
import cl.reservakids.domain.model.Reserva;
import cl.reservakids.domain.model.ReservaServicio;
import cl.reservakids.domain.model.Staff;
import cl.reservakids.domain.model.Tenant;
import cl.reservakids.domain.model.Usuario;

import java.util.List;

/**
 * Puerto de notificaciones (RF-08). La implementación de infraestructura decide el canal:
 * HTML email con Thymeleaf; sin SMTP → degrada a log.
 */
public interface NotificacionPort {

    /** Aviso al dueño de nueva solicitud pública. linkWhatsApp viene del adapter de WhatsApp. */
    void nuevaSolicitud(Tenant tenant, Reserva reserva, Cliente cliente, String linkWhatsApp);

    /** Email al cliente confirmando que su cita por hora quedó agendada (tras el pago). */
    void citaConfirmada(Tenant tenant, Reserva cita, Cliente cliente, List<ReservaServicio> servicios);

    /** Email de recuperación de contraseña (dueño). */
    void resetPassword(Usuario usuario, String tokenPlano);

    /** Reset de contraseña para cuentas de cliente (apoderados). */
    void resetPasswordCliente(CuentaCliente cuenta, String tokenPlano);

    /** Magic link de login sin contraseña. */
    void magicLink(Usuario usuario, String tokenPlano);

    /** Recordatorio 24h antes del evento. */
    void recordatorio(Tenant tenant, Reserva reserva, Cliente cliente, List<ReservaServicio> servicios);

    /** Email de bienvenida al staff con sus credenciales de acceso. */
    void staffBienvenida(Staff staff, String passwordPlana, String negocioNombre);
}
