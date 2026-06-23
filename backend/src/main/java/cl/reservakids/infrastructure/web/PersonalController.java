package cl.reservakids.infrastructure.web;

import cl.reservakids.application.dto.StaffDtos.StaffActualizarRequest;
import cl.reservakids.application.dto.StaffDtos.StaffCrearRequest;
import cl.reservakids.application.dto.StaffDtos.StaffResponse;
import cl.reservakids.application.usecase.StaffService;
import cl.reservakids.infrastructure.security.AuthPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Gestión del personal por el dueño o staff con permiso personal:gestionar.
 * Vive en {@code /api/personal} —no en {@code /api/staff/**}, que es el área del
 * propio staff (ROLE_STAFF)— porque el catch-all de SecurityConfig exige DUENO aquí.
 */
@RestController
@RequestMapping("/api/personal")
@RequiredArgsConstructor
public class PersonalController {

    private final StaffService staffService;

    @GetMapping
    @PreAuthorize("hasRole('DUENO') or hasPermission(#principal.tenantId, 'reservakids', 'personal:leer')")
    public List<StaffResponse> listar(@AuthenticationPrincipal AuthPrincipal principal) {
        return staffService.listar(principal.tenantId());
    }

    @PostMapping
    @PreAuthorize("hasRole('DUENO') or hasPermission(#principal.tenantId, 'reservakids', 'personal:gestionar')")
    public StaffResponse crear(@AuthenticationPrincipal AuthPrincipal principal,
                               @Valid @RequestBody StaffCrearRequest req) {
        return staffService.crear(principal.tenantId(), req);
    }

    @PutMapping("/{staffId}")
    @PreAuthorize("hasRole('DUENO') or hasPermission(#principal.tenantId, 'reservakids', 'personal:gestionar')")
    public StaffResponse actualizar(@PathVariable Long staffId,
                                     @AuthenticationPrincipal AuthPrincipal principal,
                                     @Valid @RequestBody StaffActualizarRequest req) {
        return staffService.actualizar(principal.tenantId(), staffId, req);
    }

    @DeleteMapping("/{staffId}")
    @PreAuthorize("hasRole('DUENO') or hasPermission(#principal.tenantId, 'reservakids', 'personal:gestionar')")
    public void eliminar(@PathVariable Long staffId,
                         @AuthenticationPrincipal AuthPrincipal principal) {
        staffService.eliminar(principal.tenantId(), staffId);
    }
}
