package cl.reservakids.infrastructure.web;

import cl.reservakids.application.usecase.ClienteService;
import cl.reservakids.infrastructure.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Ley 21.719 (falla #4, revisión a 2 años): derecho de supresión ejercido desde el panel. */
@RestController
@RequestMapping("/api/clientes")
@RequiredArgsConstructor
public class ClienteController {

    private final ClienteService clienteService;

    @PostMapping("/{id}/anonimizar")
    public ResponseEntity<Void> anonimizar(@AuthenticationPrincipal AuthPrincipal principal,
                                           @PathVariable Long id) {
        clienteService.anonimizar(principal.tenantId(), principal.usuarioId(), id);
        return ResponseEntity.noContent().build();
    }
}
