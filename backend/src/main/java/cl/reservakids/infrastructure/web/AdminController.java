package cl.reservakids.infrastructure.web;

import cl.reservakids.application.dto.AdminDtos.NegocioAdminResumen;
import cl.reservakids.application.usecase.AdminService;
import lombok.RequiredArgsConstructor;
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
    public NegocioAdminResumen suspender(@PathVariable Long id) {
        return adminService.suspender(id);
    }

    @PostMapping("/negocios/{id}/reactivar")
    public NegocioAdminResumen reactivar(@PathVariable Long id) {
        return adminService.reactivar(id);
    }
}
