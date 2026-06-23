package cl.reservakids.infrastructure.adapter;

import cl.reservakids.application.usecase.CitaTexto;
import cl.reservakids.application.usecase.NotificacionPort;
import cl.reservakids.domain.model.*;
import cl.reservakids.domain.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Adaptador de notificaciones por email con plantillas HTML (Thymeleaf).
 *
 * <p>Envío AFTER_COMMIT + async: si la transacción hace rollback, el email no sale.
 * Sin SMTP configurado (MAIL_HOST vacío), degrada a log. El contador de fallos
 * consecutivos alimenta el banner de alerta en el panel del dueño.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificacionAdapter implements NotificacionPort {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final TemplateEngine templateEngine;
    private final UsuarioRepository usuarioRepository;

    @Value("${spring.mail.host:}")
    private String mailHost;

    @Value("${app.mail.from}")
    private String remitente;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    private final AtomicInteger fallosConsecutivos = new AtomicInteger();
    private volatile String ultimoError;
    private volatile OffsetDateTime ultimoFalloEn;

    public record EstadoEnvios(int fallosConsecutivos, String ultimoFalloEn, boolean smtpConfigurado) {}

    public EstadoEnvios estadoEnvios() {
        return new EstadoEnvios(fallosConsecutivos.get(),
                ultimoFalloEn == null ? null : ultimoFalloEn.toString(),
                senderConfigurado() != null);
    }

    // ── Port methods ──

    @Override
    public void nuevaSolicitud(Tenant tenant, Reserva reserva, Cliente cliente, String linkWhatsApp) {
        ejecutarTrasCommit(() -> enviarNuevaSolicitud(tenant, reserva, cliente, linkWhatsApp));
    }

    @Override
    public void citaConfirmada(Tenant tenant, Reserva cita, Cliente cliente, List<ReservaServicio> servicios) {
        ejecutarTrasCommit(() -> enviarCitaConfirmada(tenant, cita, cliente, servicios));
    }

    @Override
    public void resetPassword(Usuario usuario, String tokenPlano) {
        ejecutarTrasCommit(() -> enviarReset(usuario, tokenPlano));
    }

    @Override
    public void resetPasswordCliente(CuentaCliente cuenta, String tokenPlano) {
        ejecutarTrasCommit(() -> enviarResetCliente(cuenta, tokenPlano));
    }

    @Override
    public void magicLink(Usuario usuario, String tokenPlano) {
        ejecutarTrasCommit(() -> enviarMagicLink(usuario, tokenPlano));
    }

    @Override
    public void recordatorio(Tenant tenant, Reserva reserva, Cliente cliente, List<ReservaServicio> servicios) {
        String destino = cliente.isAnonimizado() ? null : cliente.getEmail();
        if (destino == null || destino.isBlank()) return;
        ejecutarTrasCommit(() -> enviarRecordatorio(tenant, reserva, cliente, servicios));
    }

    @Override
    public void staffBienvenida(Staff staff, String passwordPlana, String negocioNombre) {
        ejecutarTrasCommit(() -> enviarStaffBienvenida(staff, passwordPlana, negocioNombre));
    }

    // ── Implementaciones de envío ──

    private void enviarReset(Usuario usuario, String tokenPlano) {
        String link = frontendUrl + "/reset#token=" + java.net.URLEncoder.encode(tokenPlano, java.nio.charset.StandardCharsets.UTF_8);
        Context ctx = new Context();
        ctx.setVariable("titulo", "Restablece tu contraseña");
        ctx.setVariable("link", link);
        enviarHtml("reset-password", "🔑 Restablece tu contraseña — ReservaKids", usuario.getEmail(), ctx);
    }

    private void enviarResetCliente(CuentaCliente cuenta, String tokenPlano) {
        String link = frontendUrl + "/clientes/reset/confirmar#token="
                + java.net.URLEncoder.encode(tokenPlano, java.nio.charset.StandardCharsets.UTF_8);
        Context ctx = new Context();
        ctx.setVariable("titulo", "Restablece tu contraseña");
        ctx.setVariable("nombre", cuenta.getNombre());
        ctx.setVariable("link", link);
        enviarHtml("reset-password-cliente", "🔑 Restablece tu contraseña — ReservaKids", cuenta.getEmail(), ctx);
    }

    private void enviarMagicLink(Usuario usuario, String tokenPlano) {
        String link = frontendUrl + "/magic#token=" + java.net.URLEncoder.encode(tokenPlano, java.nio.charset.StandardCharsets.UTF_8);
        Context ctx = new Context();
        ctx.setVariable("titulo", "Tu enlace para entrar");
        ctx.setVariable("link", link);
        enviarHtml("magic-link", "🔗 Tu enlace para entrar a ReservaKids", usuario.getEmail(), ctx);
    }

    private void enviarNuevaSolicitud(Tenant tenant, Reserva reserva, Cliente cliente, String linkWhatsApp) {
        String destino = usuarioRepository.findFirstByTenantIdOrderById(tenant.getId())
                .map(Usuario::getEmail).orElse(null);
        if (destino == null) return;

        Context ctx = new Context();
        ctx.setVariable("titulo", "Nueva solicitud #" + reserva.getId());
        ctx.setVariable("reservaId", reserva.getId());
        ctx.setVariable("clienteNombre", cliente.getNombre());
        ctx.setVariable("clienteTelefono", cliente.getTelefono());
        ctx.setVariable("numNinos", reserva.getNumNinos() != null ? reserva.getNumNinos() : 0);
        ctx.setVariable("comuna", reserva.getComuna());
        ctx.setVariable("comentarios", reserva.getComentarios());
        ctx.setVariable("panelUrl", frontendUrl + "/panel/solicitudes");
        ctx.setVariable("linkWhatsApp", linkWhatsApp);
        enviarHtml("nueva-solicitud",
                "🎈 Nueva solicitud #" + reserva.getId() + " — " + tenant.getNombre(),
                destino, ctx);
    }

    private void enviarCitaConfirmada(Tenant tenant, Reserva cita, Cliente cliente, List<ReservaServicio> servicios) {
        String destino = cliente.isAnonimizado() ? null : cliente.getEmail();
        if (destino == null) return;

        List<Map<String, Object>> serviciosList = new ArrayList<>();
        int total = 0;
        if (servicios != null) {
            for (ReservaServicio s : servicios) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("nombre", s.getNombre());
                m.put("precio", s.getPrecioClp() != null ? s.getPrecioClp() : 0);
                serviciosList.add(m);
                total += s.getPrecioClp() != null ? s.getPrecioClp() : 0;
            }
        }

        Context ctx = new Context();
        ctx.setVariable("titulo", "Cita confirmada");
        ctx.setVariable("clienteNombre", cliente.getNombre());
        ctx.setVariable("negocioNombre", tenant.getNombre());
        ctx.setVariable("fechaHora", CitaTexto.fechaHora(cita));
        ctx.setVariable("servicios", serviciosList);
        ctx.setVariable("total", total);
        enviarHtml("cita-confirmada",
                "✅ Cita confirmada en " + tenant.getNombre() + " — " + CitaTexto.fechaHora(cita),
                destino, ctx);
    }

    private void enviarRecordatorio(Tenant tenant, Reserva reserva, Cliente cliente, List<ReservaServicio> servicios) {
        String destino = cliente.getEmail();
        if (destino == null) return;

        List<Map<String, Object>> serviciosList = new ArrayList<>();
        if (servicios != null) {
            for (ReservaServicio s : servicios) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("nombre", s.getNombre());
                serviciosList.add(m);
            }
        }
        String fechaHora = reserva.getInicio() != null ? CitaTexto.fechaHora(reserva) : "el día agendado";

        Context ctx = new Context();
        ctx.setVariable("titulo", "Recordatorio");
        ctx.setVariable("clienteNombre", cliente.getNombre());
        ctx.setVariable("negocioNombre", tenant.getNombre());
        ctx.setVariable("fechaHora", fechaHora);
        ctx.setVariable("servicios", serviciosList);
        enviarHtml("recordatorio",
                "🔔 Recordatorio: tu reserva en " + tenant.getNombre() + " — " + fechaHora,
                destino, ctx);
    }

    private void enviarStaffBienvenida(Staff staff, String passwordPlana, String negocioNombre) {
        Context ctx = new Context();
        ctx.setVariable("titulo", "Bienvenido al equipo");
        ctx.setVariable("nombre", staff.getNombre());
        ctx.setVariable("negocioNombre", negocioNombre);
        ctx.setVariable("email", staff.getEmail());
        ctx.setVariable("password", passwordPlana);
        ctx.setVariable("rol", staff.getRol());
        ctx.setVariable("loginUrl", frontendUrl + "/staff/entrar");
        enviarHtml("staff-bienvenida",
                "👋 Bienvenido a " + negocioNombre + " — ReservaKids",
                staff.getEmail(), ctx);
    }

    // ── Motor de envío HTML ──

    private void enviarHtml(String template, String subject, String to, Context ctx) {
        try {
            JavaMailSender sender = senderConfigurado();
            if (sender == null) {
                log.info("[Email STUB] {} → {} (template={})", subject, to, template);
                return;
            }
            String html = templateEngine.process("email/base", ctx);
            // Inyectar el fragmento de contenido
            String contenido = templateEngine.process("email/" + template, ctx);
            html = html.replace("<th:block th:insert=\"${contenido}\" />", contenido);

            var mime = sender.createMimeMessage();
            var helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(remitente);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            sender.send(mime);
            fallosConsecutivos.set(0);
            log.info("Email HTML enviado: {} → {}", subject, to);
        } catch (Exception e) {
            fallosConsecutivos.incrementAndGet();
            ultimoError = e.getMessage();
            ultimoFalloEn = OffsetDateTime.now();
            log.error("Fallo enviando email '{}' a {} ({} consecutivos): {}",
                    subject, to, fallosConsecutivos.get(), e.getMessage());
        }
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

    private JavaMailSender senderConfigurado() {
        return (mailHost == null || mailHost.isBlank()) ? null : mailSenderProvider.getIfAvailable();
    }
}
