package cl.reservakids.infrastructure.adapter;

import cl.reservakids.application.usecase.AlertaOperacionesPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/**
 * Adaptador de alertas vía Slack Incoming Webhook.
 * <ul>
 *   <li><b>Stub</b> (default, sin {@code SLACK_WEBHOOK_URL}): loguea la alerta a nivel WARN.</li>
 *   <li><b>Real</b>: hace POST del payload {@code {"text": ...}} al webhook configurado.</li>
 * </ul>
 * Mismo contrato de resiliencia que el resto de notificaciones: envío asíncrono y un fallo
 * de Slack <b>nunca</b> rompe el flujo que disparó la alerta.
 */
@Slf4j
@Component
public class SlackAlertaAdapter implements AlertaOperacionesPort {

    @Value("${app.alertas.slack.webhook-url:}")
    private String webhookUrl;

    @Override
    public void alertaCritica(String titulo, String detalle) {
        String texto = titulo + (detalle == null || detalle.isBlank() ? "" : "\n" + detalle);
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.warn("[Slack STUB] {}", texto.replace("\n", " | "));
            return;
        }
        CompletableFuture.runAsync(() -> enviar(texto));
    }

    private void enviar(String texto) {
        try {
            String json = "{\"text\":\"" + escapeJson(texto) + "\"}";
            HttpURLConnection conn = (HttpURLConnection) URI.create(webhookUrl).toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }
            int status = conn.getResponseCode();
            if (status >= 200 && status < 300) {
                log.info("Alerta enviada a Slack");
            } else {
                log.warn("Slack respondió HTTP {} al enviar la alerta", status);
            }
        } catch (Exception e) {
            log.error("No se pudo enviar la alerta a Slack: {}", e.getMessage());
        }
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
