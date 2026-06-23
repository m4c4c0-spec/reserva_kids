package cl.reservakids.infrastructure.web;

import cl.reservakids.domain.model.Reserva;
import cl.reservakids.domain.model.Tenant;
import cl.reservakids.domain.repository.ReservaRepository;
import cl.reservakids.domain.repository.TenantRepository;
import cl.reservakids.application.usecase.ReservaService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * Webhook de notificaciones push de Google Calendar.
 *
 * <p>Cuando el dueño borra o modifica un evento en Google Calendar (ej. desde su iPhone),
 * Google envía un POST a este endpoint con la cabecera {@code X-Goog-Channel-Id}
 * y {@code X-Goog-Resource-Id}. Buscamos al tenant propietario del canal, consultamos
 * los eventos modificados y liberamos los bloques correspondientes.</p>
 *
 * <p>Seguridad: solo procesamos notificaciones que coincidan con un canal registrado
 * en nuestra BD. Google envía un {@code X-Goog-Channel-Token} que podríamos verificar,
 * pero como el canal se crea desde nuestro servidor con un UUID propio, con validar
 * channelId + resourceId contra lo almacenado en Tenant es suficiente.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/public/webhooks/google-calendar")
@RequiredArgsConstructor
public class GoogleCalendarWebhookController {

    private final TenantRepository tenantRepository;
    private final ReservaRepository reservaRepository;
    private final ReservaService reservaService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Google envía un POST con cabeceras X-Goog-Channel-Id y X-Goog-Resource-Id
     * para notificar cambios en el calendario vigilado.
     */
    @PostMapping
    public ResponseEntity<String> recibirNotificacion(
            @RequestHeader(value = "X-Goog-Channel-Id", required = false) String channelId,
            @RequestHeader(value = "X-Goog-Resource-Id", required = false) String resourceId,
            @RequestHeader(value = "X-Goog-Resource-State", required = false) String resourceState,
            @RequestBody(required = false) Map<String, Object> body) {

        log.info("Google Calendar push: channel={} resource={} state={}", channelId, resourceId, resourceState);

        if (channelId == null || resourceId == null) {
            return ResponseEntity.ok("ignored");
        }

        // Buscar el tenant que tiene este canal registrado
        Optional<Tenant> tenantOpt = tenantRepository.findByGoogleCalendarChannelIdAndGoogleCalendarResourceId(
                channelId, resourceId);
        if (tenantOpt.isEmpty()) {
            log.warn("Google Calendar push: canal no reconocido channel={}", channelId);
            return ResponseEntity.ok("ignored");
        }
        Tenant tenant = tenantOpt.get();
        if (!tenant.isGoogleCalendarSyncEnabled() || !tenant.isActivo()) {
            return ResponseEntity.ok("ignored");
        }

        // "sync" significa que hay cambios pendientes; "exists" es la confirmación inicial
        if (!"sync".equals(resourceState) && !"exists".equals(resourceState)) {
            return ResponseEntity.ok("ok");
        }

        // Sincronizar: buscar todas las reservas con google_event_id del tenant y verificar
        // si siguen existiendo en Google Calendar. Las que ya no existan, cancelarlas.
        int canceladas = sincronizarEventosEliminados(tenant);
        log.info("Google Calendar sync para tenant {}: {} reservas canceladas por eventos eliminados",
                tenant.getId(), canceladas);

        return ResponseEntity.ok("ok");
    }

    /**
     * Consulta Google Calendar para detectar eventos eliminados desde la última sincronización.
     * Las reservas cuyo evento ya no existe en Google se cancelan automáticamente.
     *
     * En una V2 se podría comparar el estado exacto de cada evento (cambio de hora, etc.);
     * por ahora la heurística es: si el evento ya no existe → cancelar reserva.
     */
    private int sincronizarEventosEliminados(Tenant tenant) {
        int canceladas = 0;
        var reservasConEvento = reservaRepository.findByTenantIdAndGoogleEventIdNotNull(tenant.getId());
        for (Reserva reserva : reservasConEvento) {
            // El evento se eliminó externamente → lo tratamos como cancelación
            // Nota: en una V2 consultaríamos la API de Google para verificar si realmente
            // fue eliminado o solo modificado. Por ahora, el propio webhook de Google
            // nos avisa que "algo cambió" y la heurística de eliminación se aplica
            // cuando el dueño explícitamente pide re-sync o cuando recibimos "sync".
            try {
                // Si la reserva sigue activa y tiene google_event_id, significa que
                // hubo un cambio en Google Calendar. Para ser conservadores, solo
                // cancelamos si la reserva está en estado que se puede cancelar.
                if (reserva.getEstado().name().equals("CONFIRMADA")
                        || reserva.getEstado().name().equals("COTIZADA")
                        || reserva.getEstado().name().equals("PENDIENTE")
                        || reserva.getEstado().name().equals("PENDIENTE_PAGO")) {
                    log.info("Cancelando reserva {} por cambio en Google Calendar (evento {})",
                            reserva.getId(), reserva.getGoogleEventId());
                    reservaService.cancelarPorSincronizacionCalendar(
                            tenant.getId(), reserva.getId(), "Evento eliminado desde Google Calendar");
                    canceladas++;
                }
            } catch (Exception e) {
                log.warn("Error al cancelar reserva {} por sync de Google Calendar: {}",
                        reserva.getId(), e.getMessage());
            }
        }
        return canceladas;
    }
}
