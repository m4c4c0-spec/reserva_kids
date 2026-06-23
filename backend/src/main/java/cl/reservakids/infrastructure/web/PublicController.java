package cl.reservakids.infrastructure.web;

import cl.reservakids.application.dto.CalendarioDtos.BloqueResponse;
import cl.reservakids.application.dto.ReservaDtos.ReservaResponse;
import cl.reservakids.application.dto.ReservaDtos.SolicitudPublicaRequest;
import cl.reservakids.application.dto.ServicioDtos.ServicioResponse;
import cl.reservakids.application.usecase.CalendarioService;
import cl.reservakids.application.usecase.DisponibilidadService;
import cl.reservakids.application.usecase.ReservaService;
import cl.reservakids.domain.exception.RecursoNoEncontradoException;
import cl.reservakids.domain.model.Tenant;
import cl.reservakids.domain.repository.ReservaRepository;
import cl.reservakids.domain.repository.ServicioRepository;
import cl.reservakids.domain.repository.TenantRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** RF-03/04/05: mini-sitio público por negocio (reservakids.cl/{slug}). Sin auth, rate-limited. */
@Slf4j
@RestController
@RequestMapping("/api/public/{slug}")
@RequiredArgsConstructor
public class PublicController {

    private final TenantRepository tenantRepository;
    private final ServicioRepository servicioRepository;
    private final ReservaRepository reservaRepository;
    private final CalendarioService calendarioService;
    private final ReservaService reservaService;
    private final DisponibilidadService disponibilidadService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> catalogo(@PathVariable String slug) {
        Tenant tenant = buscarTenant(slug);
        List<ServicioResponse> servicios = servicioRepository
                .findByTenantIdAndActivoTrueOrderByNombre(tenant.getId())
                .stream().map(ServicioResponse::de)
                .filter(s -> s.stock() == null || s.stock() > 0)
                .toList();
        // V26: teléfono de WhatsApp del salón para el botón flotante "¿Dudas? Habla con el
        // dueño". Lo el dueño en su panel; si no lo configuró no aparece el botón.
        Map<String, Object> body = new HashMap<>();
        body.put("nombre", tenant.getNombre());
        body.put("slug", tenant.getSlug());
        body.put("servicios", servicios);
        body.put("colorPrimario", tenant.getColorPrimario());
        if (tenant.getTituloPagina() != null && !tenant.getTituloPagina().isBlank()) {
            body.put("tituloPagina", tenant.getTituloPagina());
        }
        if (tenant.getMetaPixelId() != null && !tenant.getMetaPixelId().isBlank()) {
            body.put("metaPixelId", tenant.getMetaPixelId());
        }
        if (tenant.getPoliticasCancelacion() != null && !tenant.getPoliticasCancelacion().isBlank()) {
            body.put("politicasCancelacion", tenant.getPoliticasCancelacion());
        }
        if (tenant.getTelefonoContacto() != null && !tenant.getTelefonoContacto().isBlank()) {
            body.put("whatsapp", tenant.getTelefonoContacto());
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(60, TimeUnit.SECONDS).cachePublic())
                .body(body);
    }

    @GetMapping("/disponibilidad")
    public List<BloqueResponse> disponibilidad(@PathVariable String slug, @RequestParam String mes) {
        Tenant tenant = buscarTenant(slug);
        return calendarioService.listarDisponiblesMes(tenant.getId(), YearMonth.parse(mes));
    }

    /**
     * Agendamiento por hora: horas de inicio libres para una fecha, dada la duración total
     * (suma de los servicios elegidos). Devuelve "HH:mm" ordenadas. Vacío = sin cupo ese día.
     */
    @GetMapping("/horas")
    public List<String> horas(@PathVariable String slug,
                              @RequestParam String fecha,
                              @RequestParam int duracion) {
        Tenant tenant = buscarTenant(slug);
        return disponibilidadService.horasLibres(tenant.getId(), LocalDate.parse(fecha), duracion)
                .stream().map(LocalTime::toString).toList();
    }

    @PostMapping("/reservas")
    public ResponseEntity<ReservaResponse> crearSolicitud(@PathVariable String slug,
                                                           @Valid @RequestBody SolicitudPublicaRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reservaService.crearSolicitudPublica(slug, req));
    }

    /**
     * V30: feed iCal público suscribible desde Google Calendar / Apple Calendar.
     * Contiene todas las reservas CONFIRMADAS del negocio con fecha futura.
     * Solo muestra hora y cantidad de niños (sin precios ni datos sensibles).
     */
    @GetMapping("/calendar.ics")
    public ResponseEntity<String> feedIcal(@PathVariable String slug) {
        Tenant tenant = buscarTenant(slug);
        var reservas = reservaRepository.findByTenantIdOrderByCreadaEnDesc(tenant.getId()).stream()
                .filter(r -> "CONFIRMADA".equals(r.getEstado().name()) && r.getInicio() != null)
                .toList();

        StringBuilder ics = new StringBuilder();
        ics.append("BEGIN:VCALENDAR\r\n");
        ics.append("VERSION:2.0\r\n");
        ics.append("PRODID:-//ReservaKids//ReservaKids Calendar//ES\r\n");
        ics.append("CALSCALE:GREGORIAN\r\n");
        ics.append("METHOD:PUBLISH\r\n");
        ics.append("X-WR-CALNAME:").append(tenant.getNombre()).append(" - ReservaKids\r\n");
        ics.append("REFRESH-INTERVAL;VALUE=DURATION:PT1H\r\n");

        DateTimeFormatter icsDt = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
        ZoneId scl = ZoneId.of("America/Santiago");

        for (var r : reservas) {
            var inicio = r.getInicio().atZoneSameInstant(scl);
            var fin = r.getFin() != null ? r.getFin().atZoneSameInstant(scl) : inicio.plusHours(2);
            ics.append("BEGIN:VEVENT\r\n");
            ics.append("UID:").append(r.getId()).append("@reservakids.cl\r\n");
            ics.append("DTSTART;TZID=America/Santiago:").append(inicio.format(icsDt)).append("\r\n");
            ics.append("DTEND;TZID=America/Santiago:").append(fin.format(icsDt)).append("\r\n");
            ics.append("SUMMARY:Fiesta infantil\r\n");
            ics.append("DESCRIPTION:").append(r.getNumNinos() != null ? r.getNumNinos() + " niños" : "Cumpleaños");
            if (r.getComuna() != null) ics.append(" - ").append(r.getComuna());
            ics.append("\r\n");
            ics.append("END:VEVENT\r\n");
        }
        ics.append("END:VCALENDAR\r\n");

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/calendar; charset=UTF-8"))
                .body(ics.toString());
    }

    private Tenant buscarTenant(String slug) {
        return tenantRepository.findBySlugAndEstado(slug, "ACTIVO")
                .orElseThrow(() -> new RecursoNoEncontradoException("Negocio no encontrado"));
    }
}
