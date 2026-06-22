package cl.reservakids.infrastructure.adapter;

import cl.reservakids.application.usecase.CitaTexto;
import cl.reservakids.application.usecase.NotificacionWhatsappPort;
import cl.reservakids.domain.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Adaptador de WhatsApp con dos modos:
 * <ol>
 *   <li><b>Stub</b> (default, app.whatsapp.enabled=false): loguea el mensaje, no contacta ningún proveedor.</li>
 *   <li><b>Real</b> (app.whatsapp.enabled=true): envía vía Meta WhatsApp Cloud API.
 *       Requiere WHATSAPP_PHONE_ID y WHATSAPP_TOKEN en .env. Sin ellos, avisa y no rompe.</li>
 * </ol>
 * Mismo patrón que el email: envío AFTER_COMMIT + async; un fallo nunca rompe el flujo principal.
 */
@Slf4j
@Component
public class WhatsappStubAdapter implements NotificacionWhatsappPort {

    @Value("${app.whatsapp.enabled:false}")
    private boolean enabled;

    @Value("${app.whatsapp.phone-id:}")
    private String phoneId;

    @Value("${app.whatsapp.token:}")
    private String token;

    private final AtomicInteger fallosConsecutivos = new AtomicInteger();

    @Override
    public void confirmacionReserva(Tenant tenant, Reserva reserva, Cliente cliente,
                                    List<ReservaServicio> servicios) {
        if (cliente.isAnonimizado() || cliente.getTelefono() == null) return;
        String hora = reserva.getInicio() != null
                ? CitaTexto.fechaHora(reserva)
                : "el día agendado";
        String lista = servicios != null && !servicios.isEmpty()
                ? CitaTexto.listaServicios(servicios)
                : "";
        String mensaje = "✅ ¡Tu reserva quedó agendada en " + tenant.getNombre() + "!\n" +
                "\uD83D\uDCC5 " + hora + "\n" +
                lista +
                (lista.isEmpty() ? "" : "\n") +
                "¡Te esperamos! Te recordaremos antes de tu reserva.";
        enviar(cliente.getTelefono(), mensaje);
    }

    @Override
    public void recordatorio(Tenant tenant, Reserva reserva, Cliente cliente,
                             List<ReservaServicio> servicios) {
        if (cliente.isAnonimizado() || cliente.getTelefono() == null) return;
        String hora = reserva.getInicio() != null
                ? CitaTexto.fechaHora(reserva)
                : "mañana";
        String lista = servicios != null && !servicios.isEmpty()
                ? CitaTexto.listaServicios(servicios)
                : "Tu reserva de cumpleaños";
        String mensaje = "\uD83D\uDD14 Recordatorio " + cliente.getNombre() + "!\n\n" +
                "Mañana tienes tu reserva en " + tenant.getNombre() + ":\n\n" +
                lista +
                "\u23F0 " + hora + "\n\n" +
                "Recuerda llegar puntual. ¡Te esperamos!";
        enviar(cliente.getTelefono(), mensaje);
    }

    @Override
    public void nuevaSolicitudDueno(Tenant tenant, Reserva reserva, Cliente cliente) {
        if (cliente.isAnonimizado() || cliente.getTelefono() == null) return;
        String mensaje = "\uD83C\uDF88 ¡Nueva solicitud de reserva!\n\n" +
                "Cliente: " + cliente.getNombre() + "\n" +
                "Teléfono: " + cliente.getTelefono() + "\n" +
                "Niños: " + (reserva.getNumNinos() != null ? reserva.getNumNinos() : "—") + "\n" +
                "Revisa tu panel de ReservaKids para cotizar.";
        enviar(cliente.getTelefono(), mensaje);
    }

    private void enviar(String telefonoE164, String mensaje) {
        String destino = telefonoE164.replaceAll("[^0-9]", "");
        ejecutarTrasCommit(() -> {
            try {
                if (!enabled) {
                    log.info("[WhatsApp STUB → +{}] {}\n{}", destino, destino, mensaje);
                    return;
                }
                if (phoneId.isBlank() || token.isBlank()) {
                    log.warn("[WhatsApp] habilitado sin WHATSAPP_PHONE_ID/WHATSAPP_TOKEN — "
                            + "mensaje a +{} no enviado", destino);
                    fallosConsecutivos.incrementAndGet();
                    return;
                }
                // Meta WhatsApp Cloud API v22.0
                String url = "https://graph.facebook.com/v22.0/" + phoneId + "/messages";
                String json = """
                        {"messaging_product":"whatsapp","to":"%s","type":"text","text":{"body":"%s"}}"""
                        .formatted(destino, escapeJson(mensaje));

                HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                }
                int status = conn.getResponseCode();
                if (status >= 200 && status < 300) {
                    log.info("WhatsApp enviado a +{}", destino);
                    fallosConsecutivos.set(0);
                } else {
                    log.warn("WhatsApp HTTP {} para +{}", status, destino);
                    fallosConsecutivos.incrementAndGet();
                }
            } catch (Exception e) {
                fallosConsecutivos.incrementAndGet();
                log.error("WhatsApp error para +{}: {}", destino, e.getMessage());
            }
        });
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
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
}
