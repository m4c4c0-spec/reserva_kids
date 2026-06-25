package cl.reservakids.infrastructure.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Cliente de Google Calendar API v3 para sincronización bidireccional.
 *
 * <p>Usa el OAuth2 access token del dueño (obtenido en el flujo de login SSO con
 * scope {@code calendar}) para crear, eliminar y vigilar eventos.</p>
 *
 * <p>Push notifications: cuando el dueño borra/modifica un evento desde su iPhone
 * (vía Google Calendar sync), Google envía un POST al webhook de ReservaKids con
 * el canal de watch. El controlador {@code GoogleCalendarWebhookController} procesa
 * la notificación y libera el bloque correspondiente.</p>
 */
@Slf4j
@Component
public class GoogleCalendarAdapter {

    private static final String CALENDAR_API = "https://www.googleapis.com/calendar/v3/calendars";
    private static final DateTimeFormatter RFC3339 = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.api.url:http://localhost:8080}")
    private String apiUrl;

    @Value("${app.oauth2.google.client-id:}")
    private String googleClientId;

    @Value("${app.oauth2.google.client-secret:}")
    private String googleClientSecret;

    /**
     * Crea un evento en Google Calendar y devuelve su ID.
     *
     * @param accessToken OAuth2 access token del dueño (con scope calendar)
     * @param calendarId  ID del calendario (ej. "primary" o email)
     * @param titulo      título del evento
     * @param inicio      fecha/hora inicio
     * @param fin         fecha/hora fin
     * @param descripcion descripción opcional
     * @param location    ubicación opcional
     * @return ID del evento creado en Google Calendar
     */
    public String crearEvento(String accessToken, String calendarId, String titulo,
                               OffsetDateTime inicio, OffsetDateTime fin,
                               String descripcion, String location) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("summary", titulo);
            if (descripcion != null && !descripcion.isBlank()) {
                body.put("description", descripcion);
            }
            if (location != null && !location.isBlank()) {
                body.put("location", location);
            }

            ObjectNode start = objectMapper.createObjectNode();
            start.put("dateTime", inicio.format(RFC3339));
            start.put("timeZone", "America/Santiago");
            body.set("start", start);

            ObjectNode end = objectMapper.createObjectNode();
            end.put("dateTime", fin.format(RFC3339));
            end.put("timeZone", "America/Santiago");
            body.set("end", end);

            String url = CALENDAR_API + "/" + URLEncoder.encode(calendarId, StandardCharsets.UTF_8) + "/events";
            JsonNode response = postJson(url, accessToken, body.toString());

            String eventId = response.get("id").asText();
            log.info("Evento Google Calendar creado: {} (calendar={})", eventId, calendarId);
            return eventId;
        } catch (Exception e) {
            log.error("Error al crear evento en Google Calendar: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Elimina un evento de Google Calendar.
     *
     * @return true si se eliminó correctamente
     */
    public boolean eliminarEvento(String accessToken, String calendarId, String eventId) {
        try {
            String url = CALENDAR_API + "/" + URLEncoder.encode(calendarId, StandardCharsets.UTF_8)
                    + "/events/" + URLEncoder.encode(eventId, StandardCharsets.UTF_8);
            int status = delete(url, accessToken);
            if (status >= 200 && status < 300) {
                log.info("Evento Google Calendar eliminado: {}", eventId);
                return true;
            }
            if (status == 410 || status == 404) {
                log.info("Evento {} ya no existe en Google Calendar (ya fue borrado)", eventId);
                return true;
            }
            log.warn("Google Calendar respondió HTTP {} al eliminar evento {}", status, eventId);
            return false;
        } catch (Exception e) {
            log.error("Error al eliminar evento de Google Calendar {}: {}", eventId, e.getMessage());
            return false;
        }
    }

    /**
     * Renueva el OAuth2 access token usando el refresh token.
     *
     * @param refreshToken OAuth2 refresh token del dueño
     * @return nuevo access token, o null si falla
     */
    public String refrescarAccessToken(String refreshToken) {
        if (googleClientId.isBlank() || googleClientSecret.isBlank() || refreshToken == null) {
            return null;
        }
        try {
            String body = "client_id=" + URLEncoder.encode(googleClientId, StandardCharsets.UTF_8)
                    + "&client_secret=" + URLEncoder.encode(googleClientSecret, StandardCharsets.UTF_8)
                    + "&refresh_token=" + URLEncoder.encode(refreshToken, StandardCharsets.UTF_8)
                    + "&grant_type=refresh_token";

            String response = postForm("https://oauth2.googleapis.com/token", body);
            JsonNode json = objectMapper.readTree(response);
            if (json.has("access_token")) {
                log.info("Google Calendar: access token renovado");
                return json.get("access_token").asText();
            }
            String errorDesc = json.has("error") ? json.get("error").asText() : "sin_detalle";
            log.warn("Google: no se pudo renovar el access token (error={})", errorDesc);
            return null;
        } catch (Exception e) {
            log.error("Error al renovar access token de Google: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Registra un canal de watch en el calendario para recibir notificaciones push.
     * Google enviará POST a {@code webhookUrl} cuando cambien los eventos.
     *
     * @return [channelId, resourceId] o null si falla
     */
    public String[] watchCalendar(String accessToken, String calendarId) {
        try {
            String channelId = UUID.randomUUID().toString();
            String webhookUrl = apiUrl + "/api/public/webhooks/google-calendar";

            ObjectNode body = objectMapper.createObjectNode();
            body.put("id", channelId);
            body.put("type", "web_hook");
            body.put("address", webhookUrl);

            String url = CALENDAR_API + "/" + URLEncoder.encode(calendarId, StandardCharsets.UTF_8) + "/events/watch";
            JsonNode response = postJson(url, accessToken, body.toString());

            String resourceId = response.get("resourceId").asText();
            log.info("Google Calendar watch registrado: channel={} resource={}", channelId, resourceId);
            return new String[]{channelId, resourceId};
        } catch (Exception e) {
            log.error("Error al registrar watch de Google Calendar: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Cancela el canal de watch del calendario.
     */
    public void stopWatch(String accessToken) {
        // El stop channel solo necesita el id y resourceId; se envía a la API de channels
        // Como no tenemos el resourceId aquí, se invoca desde el caller que lo tiene.
    }

    /**
     * Obtiene información del token (útil para verificar que el scope calendar está presente).
     */
    public String obtenerTokenInfo(String accessToken) {
        try {
            String url = "https://www.googleapis.com/oauth2/v3/tokeninfo?access_token="
                    + URLEncoder.encode(accessToken, StandardCharsets.UTF_8);
            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            int status = conn.getResponseCode();
            if (status >= 200 && status < 300) {
                String response = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                JsonNode json = objectMapper.readTree(response);
                String scopes = json.has("scope") ? json.get("scope").asText() : "";
                log.debug("Google token scopes: {}", scopes);
                return scopes;
            }
            return null;
        } catch (Exception e) {
            log.debug("No se pudo verificar el token de Google: {}", e.getMessage());
            return null;
        }
    }

    private JsonNode postJson(String url, String accessToken, String jsonBody) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.setDoOutput(true);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }
        int status = conn.getResponseCode();
        String response = new String(
                (status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream()).readAllBytes(),
                StandardCharsets.UTF_8);
        if (status >= 400) {
            throw new RuntimeException("Google Calendar API HTTP " + status + ": " + response);
        }
        return objectMapper.readTree(response);
    }

    private String postForm(String url, String formBody) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.setDoOutput(true);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(formBody.getBytes(StandardCharsets.UTF_8));
        }
        int status = conn.getResponseCode();
        return new String(
                (status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream()).readAllBytes(),
                StandardCharsets.UTF_8);
    }

    private int delete(String url, String accessToken) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestMethod("DELETE");
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        return conn.getResponseCode();
    }
}
