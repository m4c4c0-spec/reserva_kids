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
import cl.reservakids.domain.repository.ServicioRepository;
import cl.reservakids.domain.repository.TenantRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** RF-03/04/05: mini-sitio público por negocio (reservakids.cl/{slug}). Sin auth, rate-limited. */
@RestController
@RequestMapping("/api/public/{slug}")
@RequiredArgsConstructor
public class PublicController {

    private final TenantRepository tenantRepository;
    private final ServicioRepository servicioRepository;
    private final CalendarioService calendarioService;
    private final ReservaService reservaService;
    private final DisponibilidadService disponibilidadService;

    @GetMapping
    public Map<String, Object> catalogo(@PathVariable String slug) {
        Tenant tenant = buscarTenant(slug);
        List<ServicioResponse> servicios = servicioRepository
                .findByTenantIdAndActivoTrueOrderByNombre(tenant.getId())
                .stream().map(ServicioResponse::de).toList();
        // V26: teléfono de WhatsApp del salón para el botón flotante "¿Dudas? Habla con el
        // dueño". Lo el dueño en su panel; si no lo configuró no aparece el botón.
        Map<String, Object> body = new HashMap<>();
        body.put("nombre", tenant.getNombre());
        body.put("slug", tenant.getSlug());
        body.put("servicios", servicios);
        if (tenant.getTelefonoContacto() != null && !tenant.getTelefonoContacto().isBlank()) {
            body.put("whatsapp", tenant.getTelefonoContacto());
        }
        return body;
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

    private Tenant buscarTenant(String slug) {
        return tenantRepository.findBySlugAndEstado(slug, "ACTIVO")
                .orElseThrow(() -> new RecursoNoEncontradoException("Negocio no encontrado"));
    }
}
