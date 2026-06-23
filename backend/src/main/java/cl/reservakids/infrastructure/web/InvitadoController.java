package cl.reservakids.infrastructure.web;

import cl.reservakids.application.dto.InvitadoDtos.*;
import cl.reservakids.application.usecase.InvitadoService;
import cl.reservakids.infrastructure.security.AuthPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reservas/{reservaId}/invitados")
@RequiredArgsConstructor
public class InvitadoController {

    private final InvitadoService invitadoService;

    @GetMapping
    public List<InvitadoResponse> listar(@PathVariable Long reservaId,
                                          @AuthenticationPrincipal AuthPrincipal principal) {
        return invitadoService.listar(principal.tenantId(), reservaId);
    }

    @GetMapping("/resumen")
    public InvitadoResumen resumen(@PathVariable Long reservaId,
                                    @AuthenticationPrincipal AuthPrincipal principal) {
        return invitadoService.resumen(principal.tenantId(), reservaId);
    }

    @PostMapping
    public InvitadoResponse agregar(@PathVariable Long reservaId,
                                     @AuthenticationPrincipal AuthPrincipal principal,
                                     @Valid @RequestBody InvitadoRequest req) {
        return invitadoService.agregar(principal.tenantId(), reservaId, req);
    }

    @DeleteMapping("/{invitadoId}")
    public void eliminar(@PathVariable Long reservaId,
                         @PathVariable Long invitadoId,
                         @AuthenticationPrincipal AuthPrincipal principal) {
        invitadoService.eliminar(principal.tenantId(), reservaId, invitadoId);
    }
}
