package cl.reservakids.infrastructure.web;

import cl.reservakids.application.dto.InvitadoDtos.*;
import cl.reservakids.application.usecase.InvitadoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/public/invitacion")
@RequiredArgsConstructor
public class RsvpPublicController {

    private final InvitadoService invitadoService;

    @GetMapping("/{token}")
    public InvitadoResponse ver(@PathVariable String token) {
        return invitadoService.buscarPorToken(token);
    }

    @PostMapping("/{token}/confirmar")
    public InvitadoResponse confirmar(@PathVariable String token,
                                       @Valid @RequestBody InvitadoRsrvRequest req) {
        return invitadoService.confirmarRsrv(token, req);
    }

    @PostMapping("/{token}/rechazar")
    public InvitadoResponse rechazar(@PathVariable String token,
                                      @Valid @RequestBody InvitadoRsrvRequest req) {
        return invitadoService.rechazarRsrv(token, req);
    }
}
