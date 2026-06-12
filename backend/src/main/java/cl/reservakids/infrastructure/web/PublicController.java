package cl.reservakids.infrastructure.web;

import cl.reservakids.application.dto.CalendarioDtos.BloqueResponse;
import cl.reservakids.application.dto.ReservaDtos.ReservaResponse;
import cl.reservakids.application.dto.ReservaDtos.SolicitudPublicaRequest;
import cl.reservakids.application.dto.ServicioDtos.ServicioResponse;
import cl.reservakids.application.usecase.CalendarioService;
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

import java.time.YearMonth;
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

    @GetMapping
    public Map<String, Object> catalogo(@PathVariable String slug) {
        Tenant tenant = buscarTenant(slug);
        List<ServicioResponse> servicios = servicioRepository
                .findByTenantIdAndActivoTrueOrderByNombre(tenant.getId())
                .stream().map(ServicioResponse::de).toList();
        return Map.of("nombre", tenant.getNombre(), "slug", tenant.getSlug(), "servicios", servicios);
    }

    @GetMapping("/disponibilidad")
    public List<BloqueResponse> disponibilidad(@PathVariable String slug, @RequestParam String mes) {
        Tenant tenant = buscarTenant(slug);
        return calendarioService.listarDisponiblesMes(tenant.getId(), YearMonth.parse(mes));
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
