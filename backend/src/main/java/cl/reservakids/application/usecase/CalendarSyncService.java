package cl.reservakids.application.usecase;

import cl.reservakids.domain.exception.RecursoNoEncontradoException;
import cl.reservakids.domain.model.*;
import cl.reservakids.domain.repository.*;
import cl.reservakids.infrastructure.adapter.GoogleCalendarAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Sincronización con Google Calendar.
 *
 * <p>Cuando una reserva se confirma, se crea un evento en Google Calendar del dueño.
 * El dueño puede verlo en su iPhone (vía sync de Google Calendar) y si lo borra desde
 * ahí, Google notifica a ReservaKids vía webhook y se libera el bloque automáticamente.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CalendarSyncService {

    private final ReservaRepository reservaRepository;
    private final ReservaServicioRepository reservaServicioRepository;
    private final TenantRepository tenantRepository;
    private final BloqueDisponibleRepository bloqueRepository;
    private final GoogleCalendarAdapter googleCalendarAdapter;

    private static final DateTimeFormatter ICS_DT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    /**
     * Sincroniza una reserva confirmada con Google Calendar: crea el evento y guarda el ID.
     * Solo actúa si el tenant tiene la sincronización activada.
     */
    @Transactional
    public void sincronizarReservaConfirmada(Long tenantId, Long reservaId) {
        Reserva reserva = reservaRepository.findByIdAndTenantId(reservaId, tenantId)
                .orElse(null);
        if (reserva == null || reserva.getGoogleEventId() != null) return;

        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        if (tenant == null || !tenant.isGoogleCalendarSyncEnabled()) return;

        // Para crear eventos en Google Calendar necesitamos el access token del dueño.
        // El access token vive en el flujo OAuth2 SSO (Usuario.oauthProvider).
        // En V2 se puede almacenar un Google refresh token en Tenant para renovarlo.
        // Por ahora, el evento se sincroniza cuando el dueño está autenticado.
        log.info("Google Calendar sync: tenant {} no tiene token activo — omitiendo creación de evento para reserva {}",
                tenantId, reservaId);
    }

    /**
     * Crea un evento en Google Calendar para una reserva confirmada.
     * Se llama durante el flujo de confirmación si el dueño está autenticado con Google.
     *
     * @param googleAccessToken OAuth2 access token del dueño (con scope calendar)
     */
    @Transactional
    public String crearEventoGoogle(Long tenantId, Long reservaId, String googleAccessToken) {
        Reserva reserva = reservaRepository.findByIdAndTenantId(reservaId, tenantId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Reserva no encontrada"));

        if (reserva.getGoogleEventId() != null) {
            return reserva.getGoogleEventId(); // ya sincronizado
        }

        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();
        List<ReservaServicio> servicios = reservaServicioRepository.findByReservaIdOrderById(reservaId);

        OffsetDateTime inicio;
        OffsetDateTime fin;
        String titulo;

        if (reserva.getInicio() != null) {
            inicio = reserva.getInicio();
            fin = reserva.getFin();
            titulo = "\uD83C\uDF89 Cita en " + tenant.getNombre();
        } else {
            BloqueDisponible bloque = bloqueRepository.findById(reserva.getBloqueId()).orElse(null);
            if (bloque == null) return null;
            java.time.ZoneId zona = java.time.ZoneId.of("America/Santiago");
            inicio = bloque.getFecha().atTime(bloque.getHoraInicio()).atZone(zona).toOffsetDateTime();
            fin = bloque.getFecha().atTime(bloque.getHoraFin()).atZone(zona).toOffsetDateTime();
            titulo = "\uD83C\uDF88 Cumpleaños en " + tenant.getNombre();
        }

        StringBuilder desc = new StringBuilder("ReservaKids #").append(reserva.getId());
        if (!servicios.isEmpty()) {
            desc.append("\n");
            for (ReservaServicio s : servicios) {
                desc.append("• ").append(s.getNombre()).append(" ($").append(s.getPrecioClp()).append(")\n");
            }
        }
        if (reserva.getNumNinos() != null) {
            desc.append(reserva.getNumNinos()).append(" niños");
        }

        String calendarId = tenant.getGoogleCalendarId() != null
                ? tenant.getGoogleCalendarId() : "primary";

        String eventId = googleCalendarAdapter.crearEvento(
                googleAccessToken, calendarId, titulo, inicio, fin,
                desc.toString(), tenant.getNombre());

        if (eventId != null) {
            reserva.setGoogleEventId(eventId);
            reservaRepository.save(reserva);
            log.info("Reserva {} sincronizada con Google Calendar: evento {}", reservaId, eventId);
        }
        return eventId;
    }

    /**
     * Elimina el evento de Google Calendar asociado a una reserva cancelada.
     */
    @Transactional
    public void eliminarEventoGoogle(Long tenantId, Long reservaId, String googleAccessToken) {
        Reserva reserva = reservaRepository.findByIdAndTenantId(reservaId, tenantId).orElse(null);
        if (reserva == null || reserva.getGoogleEventId() == null) return;

        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        if (tenant == null) return;

        String calendarId = tenant.getGoogleCalendarId() != null
                ? tenant.getGoogleCalendarId() : "primary";

        googleCalendarAdapter.eliminarEvento(googleAccessToken, calendarId, reserva.getGoogleEventId());
        reserva.setGoogleEventId(null);
        reservaRepository.save(reserva);
    }

    /**
     * Genera el contenido de un archivo .ics para una reserva confirmada.
     */
    @Transactional(readOnly = true)
    public String generarIcs(Long tenantId, Long reservaId) {
        Reserva reserva = reservaRepository.findByIdAndTenantId(reservaId, tenantId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Reserva no encontrada"));

        if (reserva.getEstado() != EstadoReserva.CONFIRMADA) {
            throw new IllegalArgumentException("Solo se puede sincronizar reservas confirmadas");
        }

        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();
        List<ReservaServicio> servicios = reservaServicioRepository.findByReservaIdOrderById(reservaId);

        OffsetDateTime inicio;
        OffsetDateTime fin;
        String titulo;

        if (reserva.getInicio() != null) {
            inicio = reserva.getInicio();
            fin = reserva.getFin();
            titulo = "Cita en " + tenant.getNombre();
        } else {
            BloqueDisponible bloque = bloqueRepository.findById(reserva.getBloqueId()).orElse(null);
            if (bloque == null) {
                throw new IllegalStateException("Bloque no encontrado para la reserva");
            }
            inicio = bloque.getFecha().atTime(bloque.getHoraInicio())
                    .atZone(bloque.getFecha().atStartOfDay(java.time.ZoneId.of("America/Santiago")).getZone())
                    .toOffsetDateTime();
            fin = bloque.getFecha().atTime(bloque.getHoraFin())
                    .atZone(bloque.getFecha().atStartOfDay(java.time.ZoneId.of("America/Santiago")).getZone())
                    .toOffsetDateTime();
            titulo = "Cumpleaños en " + tenant.getNombre();
        }

        StringBuilder descripcion = new StringBuilder();
        if (!servicios.isEmpty()) {
            for (ReservaServicio s : servicios) {
                descripcion.append("• ").append(s.getNombre()).append(" ($").append(s.getPrecioClp()).append(")\\n");
            }
        } else if (reserva.getServicioId() != null) {
            descripcion.append("Reserva de cumpleaños #").append(reserva.getId());
        }

        return "BEGIN:VCALENDAR\r\n" +
                "VERSION:2.0\r\n" +
                "PRODID:-//ReservaKids//ReservaKids Calendar//ES\r\n" +
                "BEGIN:VEVENT\r\n" +
                "UID:" + reserva.getId() + "@reservakids.cl\r\n" +
                "DTSTAMP:" + OffsetDateTime.now().format(ICS_DT) + "\r\n" +
                "DTSTART:" + inicio.format(ICS_DT) + "\r\n" +
                "DTEND:" + fin.format(ICS_DT) + "\r\n" +
                "SUMMARY:" + titulo + "\r\n" +
                "DESCRIPTION:" + descripcion + "\r\n" +
                "LOCATION:" + tenant.getNombre() + "\r\n" +
                "END:VEVENT\r\n" +
                "END:VCALENDAR\r\n";
    }

    /**
     * Genera un link "Add to Google Calendar" para abrir directamente en el navegador.
     */
    @Transactional(readOnly = true)
    public String generarGoogleCalendarLink(Long tenantId, Long reservaId) {
        Reserva reserva = reservaRepository.findByIdAndTenantId(reservaId, tenantId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Reserva no encontrada"));

        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();

        String titulo;
        OffsetDateTime inicio;
        OffsetDateTime fin;

        if (reserva.getInicio() != null) {
            inicio = reserva.getInicio();
            fin = reserva.getFin();
            titulo = "Cita en " + tenant.getNombre();
        } else {
            BloqueDisponible bloque = bloqueRepository.findById(reserva.getBloqueId()).orElse(null);
            if (bloque == null) return null;
            java.time.ZoneId zona = java.time.ZoneId.of("America/Santiago");
            inicio = bloque.getFecha().atTime(bloque.getHoraInicio()).atZone(zona).toOffsetDateTime();
            fin = bloque.getFecha().atTime(bloque.getHoraFin()).atZone(zona).toOffsetDateTime();
            titulo = "Cumpleaños en " + tenant.getNombre();
        }

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
        String dates = inicio.format(fmt) + "/" + fin.format(fmt);

        return "https://calendar.google.com/calendar/render?action=TEMPLATE" +
                "&text=" + URLEncoder.encode(titulo, StandardCharsets.UTF_8) +
                "&dates=" + dates +
                "&details=" + URLEncoder.encode("ReservaKids #" + reserva.getId(), StandardCharsets.UTF_8) +
                "&location=" + URLEncoder.encode(tenant.getNombre(), StandardCharsets.UTF_8);
    }
}
