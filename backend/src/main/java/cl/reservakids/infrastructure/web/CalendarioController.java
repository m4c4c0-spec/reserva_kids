package cl.reservakids.infrastructure.web;

import cl.reservakids.application.dto.CalendarioDtos.BloqueRequest;
import cl.reservakids.application.dto.CalendarioDtos.BloqueResponse;
import cl.reservakids.application.usecase.CalendarioService;
import cl.reservakids.infrastructure.security.AuthPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.util.List;

/** RF-04: gestión del calendario de bloques disponibles. */
@RestController
@RequestMapping("/api/calendario/bloques")
@RequiredArgsConstructor
public class CalendarioController {

    private final CalendarioService calendarioService;

    @GetMapping
    public List<BloqueResponse> listar(@AuthenticationPrincipal AuthPrincipal principal,
                                       @RequestParam String mes) {
        return calendarioService.listarMes(principal.tenantId(), YearMonth.parse(mes));
    }

    @PostMapping
    public ResponseEntity<BloqueResponse> crear(@AuthenticationPrincipal AuthPrincipal principal,
                                                @Valid @RequestBody BloqueRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(calendarioService.crear(principal.tenantId(), req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@AuthenticationPrincipal AuthPrincipal principal,
                                         @PathVariable Long id) {
        calendarioService.eliminar(principal.tenantId(), id);
        return ResponseEntity.noContent().build();
    }
}
