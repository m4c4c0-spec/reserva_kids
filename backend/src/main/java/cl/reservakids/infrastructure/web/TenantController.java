package cl.reservakids.infrastructure.web;

import cl.reservakids.application.dto.TenantDtos.CerrarRequest;
import cl.reservakids.application.dto.TenantDtos.ExportResponse;
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

    @GetMapping("/export")
    public ExportResponse exportar(@AuthenticationPrincipal AuthPrincipal principal) {
        return tenantService.exportar(principal.tenantId());
    }

    @PostMapping("/cerrar")
    public ExportResponse cerrar(@AuthenticationPrincipal AuthPrincipal principal,
                                 @Valid @RequestBody CerrarRequest req) {
        return tenantService.cerrar(principal.tenantId(), req);
    }
}
