package cl.reservakids.infrastructure.web;

import cl.reservakids.application.dto.AdminDtos.AdminResumen;
import cl.reservakids.application.dto.AdminDtos.AuditoriaItem;
import cl.reservakids.application.dto.AdminDtos.CrearAdminRequest;
import cl.reservakids.application.dto.AdminDtos.MetricasGlobales;
import cl.reservakids.application.dto.AdminDtos.NegocioAdminResumen;
import cl.reservakids.application.usecase.AdminService;
import cl.reservakids.infrastructure.security.AuthPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Consola de plataforma del administrador (F2). Protegida por {@code SecurityConfig}:
 * {@code /api/admin/**} exige ROLE_ADMIN. Operaciones cross-tenant (gobierno de negocios).
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    /** KPIs globales de plataforma (F4). */
    @GetMapping("/metricas")
    public MetricasGlobales metricas() {
        return adminService.metricas();
    }

    /** Listado de negocios, filtrable por estado (ACTIVO/SUSPENDIDO/CERRADO) y texto. */
    @GetMapping("/negocios")
    public List<NegocioAdminResumen> negocios(@RequestParam(required = false) String estado,
                                              @RequestParam(required = false) String q) {
        return adminService.listarNegocios(estado, q);
    }

    @GetMapping("/negocios/{id}")
    public NegocioAdminResumen negocio(@PathVariable Long id) {
        return adminService.detalle(id);
    }

    @PostMapping("/negocios/{id}/suspender")
    public NegocioAdminResumen suspender(@PathVariable Long id, @AuthenticationPrincipal AuthPrincipal admin) {
        return adminService.suspender(id, admin.usuarioId());
    }

    @PostMapping("/negocios/{id}/reactivar")
    public NegocioAdminResumen reactivar(@PathVariable Long id, @AuthenticationPrincipal AuthPrincipal admin) {
        return adminService.reactivar(id, admin.usuarioId());
    }

    // ── F5: bitácora + gestión multi-admin ──

    @GetMapping("/auditoria")
    public List<AuditoriaItem> auditoria(@RequestParam(defaultValue = "50") int limite) {
        return adminService.auditoria(limite);
    }

    @GetMapping("/administradores")
    public List<AdminResumen> administradores() {
        return adminService.listarAdmins();
    }

    @PostMapping("/administradores")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminResumen crearAdmin(@Valid @RequestBody CrearAdminRequest req,
                                   @AuthenticationPrincipal AuthPrincipal admin) {
        return adminService.crearAdmin(req, admin.usuarioId());
    }
}
