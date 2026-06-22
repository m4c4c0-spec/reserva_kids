package cl.reservakids.infrastructure.web;

import cl.reservakids.application.dto.SuscripcionDtos.*;
import cl.reservakids.application.dto.TenantDtos.CerrarRequest;
import cl.reservakids.application.dto.TenantDtos.ExportResponse;
import cl.reservakids.application.usecase.SuscripcionService;
import cl.reservakids.application.usecase.TenantExportService;
import cl.reservakids.application.usecase.TenantService;
import cl.reservakids.infrastructure.security.AuthPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Offboarding (falla 3.3, revisión a 5 años): el dueño exporta sus datos cuando quiera
 * y cierra su negocio sin intervención del operador. El POST de cierre devuelve el export
 * final — la última copia antes de la purga diferida.
 */
@RestController
@RequestMapping("/api/tenant")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;
    private final TenantExportService tenantExportService;
    private final SuscripcionService suscripcionService;

    @GetMapping("/export")
    public ExportResponse exportar(@AuthenticationPrincipal AuthPrincipal principal) {
        return tenantExportService.exportar(principal.tenantId());
    }

    @PutMapping("/configuracion")
    public void actualizarConfiguracion(@AuthenticationPrincipal AuthPrincipal principal,
                                        @Valid @RequestBody cl.reservakids.application.dto.TenantDtos.ActualizarTokenRequest req) {
        tenantService.actualizarTokenMp(principal.tenantId(), principal.usuarioId(), req);
    }

    /** V25: configuración multi-pasarela (Mercado Pago / Khipu). */
    @PutMapping("/configuracion/pasarela")
    public void actualizarPasarela(@AuthenticationPrincipal AuthPrincipal principal,
                                   @Valid @RequestBody cl.reservakids.application.dto.TenantDtos.ActualizarConfigRequest req) {
        tenantService.guardarConfiguracion(principal.tenantId(), principal.usuarioId(), req);
    }

    /** V26: teléfono de WhatsApp del salón (botón flotante de ayuda en el mini-sitio público). */
    @PutMapping("/contacto")
    public void actualizarContacto(@AuthenticationPrincipal AuthPrincipal principal,
                                    @RequestBody cl.reservakids.application.dto.TenantDtos.ActualizarContactoRequest req) {
        tenantService.guardarContacto(principal.tenantId(), principal.usuarioId(), req);
    }

    @PostMapping("/cerrar")
    public ExportResponse cerrar(@AuthenticationPrincipal AuthPrincipal principal,
                                 @Valid @RequestBody CerrarRequest req) {
        return tenantService.cerrar(principal.tenantId(), principal.usuarioId(), req);
    }

    // ── Suscripción SaaS (facturación) ──

    @GetMapping("/suscripcion")
    public SuscripcionResponse estadoSuscripcion(@AuthenticationPrincipal AuthPrincipal principal) {
        return suscripcionService.estadoSuscripcion(principal.tenantId());
    }

    @GetMapping("/suscripcion/planes")
    public List<PlanResponse> listarPlanes() {
        return suscripcionService.listarPlanes();
    }

    @PostMapping("/suscripcion/contratar")
    public SuscripcionPagoResponse contratar(@AuthenticationPrincipal AuthPrincipal principal,
                                             @Valid @RequestBody SuscripcionRequest req) {
        return suscripcionService.contratar(principal.tenantId(), req);
    }

    @GetMapping("/suscripcion/historial")
    public List<SuscripcionPagoResponse> historialSuscripcion(@AuthenticationPrincipal AuthPrincipal principal) {
        return suscripcionService.historial(principal.tenantId());
    }
}
