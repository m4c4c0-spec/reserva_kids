package cl.reservakids.infrastructure.web;

import cl.reservakids.application.dto.TenantDtos.CerrarRequest;
import cl.reservakids.application.dto.TenantDtos.ExportResponse;
import cl.reservakids.application.usecase.TenantExportService;
import cl.reservakids.application.usecase.TenantService;
import cl.reservakids.infrastructure.security.AuthPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/export")
    public ExportResponse exportar(@AuthenticationPrincipal AuthPrincipal principal) {
        return tenantExportService.exportar(principal.tenantId());
    }

    @PutMapping("/configuracion")
    public void actualizarConfiguracion(@AuthenticationPrincipal AuthPrincipal principal,
                                        @Valid @RequestBody cl.reservakids.application.dto.TenantDtos.ActualizarTokenRequest req) {
        tenantService.actualizarTokenMp(principal.tenantId(), req);
    }

    @PostMapping("/cerrar")
    public ExportResponse cerrar(@AuthenticationPrincipal AuthPrincipal principal,
                                 @Valid @RequestBody CerrarRequest req) {
        return tenantService.cerrar(principal.tenantId(), req);
    }
}
