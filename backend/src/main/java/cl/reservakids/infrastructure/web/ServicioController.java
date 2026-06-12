package cl.reservakids.infrastructure.web;

import cl.reservakids.application.dto.ServicioDtos.ServicioRequest;
import cl.reservakids.application.dto.ServicioDtos.ServicioResponse;
import cl.reservakids.application.usecase.ServicioService;
import cl.reservakids.infrastructure.security.AuthPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** RF-02: panel del negocio — CRUD de servicios (siempre acotado al tenant del JWT). */
@RestController
@RequestMapping("/api/servicios")
@RequiredArgsConstructor
public class ServicioController {

    private final ServicioService servicioService;

    @GetMapping
    public List<ServicioResponse> listar(@AuthenticationPrincipal AuthPrincipal principal) {
        return servicioService.listar(principal.tenantId());
    }

    @PostMapping
    public ResponseEntity<ServicioResponse> crear(@AuthenticationPrincipal AuthPrincipal principal,
                                                  @Valid @RequestBody ServicioRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(servicioService.crear(principal.tenantId(), req));
    }

    @PutMapping("/{id}")
    public ServicioResponse actualizar(@AuthenticationPrincipal AuthPrincipal principal,
                                       @PathVariable Long id,
                                       @Valid @RequestBody ServicioRequest req) {
        return servicioService.actualizar(principal.tenantId(), id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desactivar(@AuthenticationPrincipal AuthPrincipal principal,
                                           @PathVariable Long id) {
        servicioService.desactivar(principal.tenantId(), id);
        return ResponseEntity.noContent().build();
    }
}
