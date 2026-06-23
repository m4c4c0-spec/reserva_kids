package cl.reservakids.infrastructure.web;

import cl.reservakids.application.dto.StaffDtos.*;
import cl.reservakids.application.usecase.RbacService;
import cl.reservakids.domain.model.Permiso;
import cl.reservakids.domain.model.RolPersonal;
import cl.reservakids.infrastructure.security.AuthPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class RolController {

    private final RbacService rbacService;

    @GetMapping
    @PreAuthorize("hasRole('DUENO')")
    public List<RolResponse> listar(@AuthenticationPrincipal AuthPrincipal principal) {
        return rbacService.listarRoles(principal.tenantId()).stream()
                .map(this::aResponse).toList();
    }

    @GetMapping("/permisos")
    @PreAuthorize("hasRole('DUENO')")
    public List<PermisoResponse> listarPermisos() {
        return rbacService.listarPermisos().stream()
                .map(p -> new PermisoResponse(p.getId(), p.getCodigo(), p.getNombre(), p.getCategoria()))
                .toList();
    }

    @PostMapping
    @PreAuthorize("hasRole('DUENO')")
    @ResponseStatus(HttpStatus.CREATED)
    public RolResponse crear(@AuthenticationPrincipal AuthPrincipal principal,
                             @Valid @RequestBody RolCrearRequest req) {
        RolPersonal rol = rbacService.crearRol(principal.tenantId(), req.nombre(), req.descripcion(), req.permisos());
        return aResponse(rol);
    }

    @PutMapping("/{rolId}")
    @PreAuthorize("hasRole('DUENO')")
    public RolResponse actualizar(@AuthenticationPrincipal AuthPrincipal principal,
                                   @PathVariable Long rolId,
                                   @Valid @RequestBody RolActualizarRequest req) {
        RolPersonal rol = rbacService.actualizarRol(principal.tenantId(), rolId,
                req.nombre(), req.descripcion(), req.permisos());
        return aResponse(rol);
    }

    @DeleteMapping("/{rolId}")
    @PreAuthorize("hasRole('DUENO')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@AuthenticationPrincipal AuthPrincipal principal,
                         @PathVariable Long rolId) {
        rbacService.eliminarRol(principal.tenantId(), rolId);
    }

    private RolResponse aResponse(RolPersonal rol) {
        List<PermisoResponse> permisos = rol.getPermisos().stream()
                .map(p -> new PermisoResponse(p.getId(), p.getCodigo(), p.getNombre(), p.getCategoria()))
                .toList();
        return new RolResponse(rol.getId(), rol.getNombre(), rol.getDescripcion(), permisos,
                rol.getCreadoEn() != null ? rol.getCreadoEn().toString() : null);
    }
}
