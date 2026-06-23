package cl.reservakids.infrastructure.web;

import cl.reservakids.application.dto.HorarioAtencionDtos.HorarioRequest;
import cl.reservakids.application.dto.HorarioAtencionDtos.HorarioResponse;
import cl.reservakids.application.usecase.HorarioAtencionService;
import cl.reservakids.infrastructure.security.AuthPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Panel del dueño: configuración del horario de atención para citas por hora. */
@RestController
@RequestMapping("/api/horario-atencion")
@RequiredArgsConstructor
public class HorarioAtencionController {

    private final HorarioAtencionService horarioService;

    @GetMapping
    public HorarioResponse obtener(@AuthenticationPrincipal AuthPrincipal principal) {
        return horarioService.obtener(principal.tenantId());
    }

    @PutMapping
    public ResponseEntity<Void> guardar(@AuthenticationPrincipal AuthPrincipal principal,
                                        @Valid @RequestBody HorarioRequest req) {
        horarioService.guardar(principal.tenantId(), principal.usuarioId(), req);
        return ResponseEntity.noContent().build();
    }
}
