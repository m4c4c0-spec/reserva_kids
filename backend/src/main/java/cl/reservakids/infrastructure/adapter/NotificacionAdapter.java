package cl.reservakids.infrastructure.adapter;

import cl.reservakids.application.usecase.NotificacionPort;
import cl.reservakids.domain.model.Cliente;
import cl.reservakids.domain.model.Reserva;
import cl.reservakids.domain.model.Tenant;
import cl.reservakids.domain.model.Usuario;
import cl.reservakids.domain.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * RF-08 — Sprint 5: email real al dueño por nueva solicitud + link wa.me pre-armado.
 *
 * El envío se registra como sincronización AFTER_COMMIT: si la transacción de la reserva
 * hace rollback (p. ej. el índice único anti doble-reserva), el email jamás sale —
 * sin esto el dueño recibiría correos de solicitudes que no existen.
 * Se ejecuta en un hilo aparte para no bloquear la respuesta HTTP, y un fallo de SMTP
 * solo se loguea: nunca rompe el flujo de reserva.
 *
 * Si no hay SMTP configurado (MAIL_HOST vacío), degrada a log.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificacionAdapter implements NotificacionPort {

    /** ObjectProvider: JavaMailSender solo existe si spring.mail.host está definido. */
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final UsuarioRepository usuarioRepository;

    /**
     * Con `spring.mail.host: ${MAIL_HOST:}` la propiedad SIEMPRE existe (vacía), así que
     * Spring Boot crea el JavaMailSender igual — y enviar con host "" lanza excepción en
     * vez de degradar a log. Este guard hace verdadero el contrato "vacío = solo log".
     */
    @Value("${spring.mail.host:}")
    private String mailHost;

    @Value("${app.mail.from}")
    private String remitente;

    /** null cuando no hay SMTP configurado (host en blanco) — el llamador degrada a log. */
    private JavaMailSender senderConfigurado() {
        return (mailHost == null || mailHost.isBlank()) ? null : mailSenderProvider.getIfAvailable();
    }

    /** Base de los enlaces que viajan por email (reset de contraseña). */
    @Value("${app.frontend.url}")
    private String frontendUrl;

    /**
     * Falla #3 (revisión a 2 años): un fallo de SMTP solo se logueaba — si la API key expira
     * o el correo cae a spam, el dueño deja de enterarse de solicitudes nuevas sin ninguna
     * señal. Este contador alimenta GET /api/sistema/notificaciones y el aviso del panel.
     */
    private final AtomicInteger fallosConsecutivos = new AtomicInteger();
    // ultimoError se conserva SOLO para los logs del servidor: puede contener el email de
    // destino de otro negocio o detalles del SMTP, así que NO viaja en la respuesta del
    // endpoint (S2: /api/sistema/notificaciones lo ve cualquier usuario autenticado).
    private volatile String ultimoError;
    private volatile OffsetDateTime ultimoFalloEn;

    /**
     * S2: el estado del canal SMTP es infraestructura compartida — el contador y la marca de
     * tiempo del último fallo bastan para que el panel muestre el aviso. El mensaje de error
     * crudo (ultimoError) queda fuera para no filtrar datos de otro tenant entre negocios.
     */
    public record EstadoEnvios(int fallosConsecutivos, String ultimoFalloEn, boolean smtpConfigurado) {}

    public EstadoEnvios estadoEnvios() {
        return new EstadoEnvios(fallosConsecutivos.get(),
                ultimoFalloEn == null ? null : ultimoFalloEn.toString(),
                senderConfigurado() != null);
    }

    @Override
    public void nuevaSolicitud(Tenant tenant, Reserva reserva, Cliente cliente) {
        ejecutarTrasCommit(() -> enviar(tenant, reserva, cliente));
    }

    /**
     * Falla 1.3 (revisión a 5 años): email de recuperación de contraseña. AFTER_COMMIT
     * como toda notificación: el token debe existir en BD antes de que el enlace llegue.
     */
    @Override
    public void resetPassword(Usuario usuario, String tokenPlano) {
        ejecutarTrasCommit(() -> enviarReset(usuario, tokenPlano));
    }

    private void ejecutarTrasCommit(Runnable envio) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    CompletableFuture.runAsync(envio);
                }
            });
        } else {
            CompletableFuture.runAsync(envio);
        }
    }

    private void enviarReset(Usuario usuario, String tokenPlano) {
        String link = frontendUrl + "/reset?token=" + URLEncoder.encode(tokenPlano, StandardCharsets.UTF_8);
        try {
            JavaMailSender sender = senderConfigurado();
            if (sender == null) {
                // Sin SMTP el log es el único canal (MVP): el operador puede pasar el enlace
                // a mano. Con SMTP configurado el enlace NUNCA se loguea (es una credencial).
                log.info("Reset de contraseña solicitado para {} [email no configurado]: {}",
                        usuario.getEmail(), link);
                return;
            }
            SimpleMailMessage mensaje = new SimpleMailMessage();
            mensaje.setFrom(remitente);
            mensaje.setTo(usuario.getEmail());
            mensaje.setSubject("🔑 Restablece tu contraseña — ReservaKids");
            mensaje.setText("""
                    Recibimos una solicitud para restablecer tu contraseña.

                    Crea una nueva aquí (el enlace vence en 30 minutos y sirve UNA vez):
                    %s

                    Si no lo pediste, ignora este correo: tu contraseña sigue igual.
                    """.formatted(link));
            sender.send(mensaje);
            fallosConsecutivos.set(0);
            log.info("Email de reset de contraseña enviado a {}", usuario.getEmail());
        } catch (Exception e) {
            fallosConsecutivos.incrementAndGet();
            ultimoError = e.getMessage();
            ultimoFalloEn = OffsetDateTime.now();
            log.error("Fallo enviando reset de contraseña a {} ({} consecutivos): {}",
                    usuario.getEmail(), fallosConsecutivos.get(), e.getMessage());
        }
    }

    private void enviar(Tenant tenant, Reserva reserva, Cliente cliente) {
        try {
            String destino = usuarioRepository.findFirstByTenantIdOrderById(tenant.getId())
                    .map(u -> u.getEmail()).orElse(null);
            JavaMailSender sender = senderConfigurado();

            if (sender == null || destino == null) {
                log.info("Nueva solicitud #{} para tenant '{}' — cliente {} ({}) [email no configurado, solo log]",
                        reserva.getId(), tenant.getSlug(), cliente.getNombre(), cliente.getTelefono());
                return;
            }

            SimpleMailMessage mensaje = new SimpleMailMessage();
            mensaje.setFrom(remitente);
            mensaje.setTo(destino);
            mensaje.setSubject("🎈 Nueva solicitud de reserva #%d — %s"
                    .formatted(reserva.getId(), tenant.getNombre()));
            mensaje.setText("""
                    ¡Tienes una nueva solicitud de reserva!

                    Solicitud: #%d
                    Cliente: %s
                    Teléfono: %s
                    Niños: %s · Comuna: %s
                    Comentarios: %s

                    Responde por WhatsApp: %s

                    Tienes 48 horas antes de que la solicitud expire automáticamente.
                    Gestiona la solicitud en tu panel de ReservaKids.
                    """.formatted(
                    reserva.getId(), cliente.getNombre(), cliente.getTelefono(),
                    reserva.getNumNinos() == null ? "—" : reserva.getNumNinos(),
                    reserva.getComuna() == null ? "—" : reserva.getComuna(),
                    reserva.getComentarios() == null ? "—" : reserva.getComentarios(),
                    linkWhatsApp(cliente, reserva)));
            sender.send(mensaje);
            fallosConsecutivos.set(0); // el canal volvió a funcionar
            log.info("Email de nueva solicitud #{} enviado a {}", reserva.getId(), destino);
        } catch (Exception e) {
            fallosConsecutivos.incrementAndGet();
            ultimoError = e.getMessage();
            ultimoFalloEn = OffsetDateTime.now();
            log.error("Fallo enviando email de solicitud #{} ({} consecutivos): {}",
                    reserva.getId(), fallosConsecutivos.get(), e.getMessage());
        }
    }

    @Override
    public String linkWhatsApp(Cliente cliente, Reserva reserva) {
        if (cliente.isAnonimizado()) {
            return null; // su placeholder no es un teléfono (Ley 21.719)
        }
        String telefono = cliente.getTelefono().replaceAll("[^0-9]", "");
        String mensaje = "Hola %s! Te escribo por tu solicitud de reserva #%d 🎉"
                .formatted(cliente.getNombre(), reserva.getId());
                
        if (reserva.getEstado() == cl.reservakids.domain.model.EstadoReserva.COTIZADA && reserva.getMpInitPoint() != null) {
            mensaje += "\n\nPuedes pagar la seña de $" + reserva.getSeniaClp() + " directamente aquí de forma segura con Mercado Pago:\n" + reserva.getMpInitPoint();
        }
        
        return "https://wa.me/" + telefono + "?text=" + URLEncoder.encode(mensaje, StandardCharsets.UTF_8);
    }
}
