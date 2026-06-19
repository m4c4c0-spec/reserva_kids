package cl.reservakids.infrastructure.web;

import cl.reservakids.application.dto.AgendaDtos.AgendarCitaRequest;
import cl.reservakids.application.dto.AgendaDtos.AgendarCitaResponse;
import cl.reservakids.application.usecase.AgendaService;
import cl.reservakids.infrastructure.security.AuthPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Área de cliente autenticado (rol CLIENTE): agendar citas por hora. */
@RestController
@RequestMapping("/api/cliente/agendar")
@RequiredArgsConstructor
public class AgendaController {

    private final AgendaService agendaService;

    /** Crea la cita (PENDIENTE_PAGO) y devuelve el link de pago. El subject del JWT es la cuenta. */
    @PostMapping("/{slug}")
    public ResponseEntity<AgendarCitaResponse> agendar(@AuthenticationPrincipal AuthPrincipal principal,
                                                       @PathVariable String slug,
                                                       @Valid @RequestBody AgendarCitaRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(agendaService.agendar(principal.usuarioId(), slug, req));
    }
}
