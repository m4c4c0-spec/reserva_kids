package cl.reservakids.infrastructure.web;

import cl.reservakids.application.dto.ReservaDtos.*;
import cl.reservakids.application.usecase.CalendarSyncService;
import cl.reservakids.application.usecase.ReservaService;
import cl.reservakids.domain.model.EstadoReserva;
import cl.reservakids.infrastructure.security.AuthPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** RF-06/07: bandeja de solicitudes, máquina de estados y registro de señas. */
@RestController
@RequestMapping("/api/reservas")
@RequiredArgsConstructor
public class ReservaController {

    private final ReservaService reservaService;
    private final CalendarSyncService calendarSyncService;

    @GetMapping
    public Page<ReservaResponse> listar(@AuthenticationPrincipal AuthPrincipal principal,
                                        @RequestParam(required = false) EstadoReserva estado,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        return reservaService.listar(principal.tenantId(), estado,
                PageRequest.of(page, Math.min(size, 100)));
    }

    @PutMapping("/{id}/cotizar")
    public ReservaResponse cotizar(@AuthenticationPrincipal AuthPrincipal principal,
                                   @PathVariable Long id,
                                   @Valid @RequestBody CotizarRequest req) {
        return reservaService.cotizar(principal.tenantId(), principal.usuarioId(), id, req);
    }

    @PutMapping("/{id}/confirmar")
    public ReservaResponse confirmar(@AuthenticationPrincipal AuthPrincipal principal,
                                     @PathVariable Long id) {
        return reservaService.confirmar(principal.tenantId(), principal.usuarioId(), id);
    }

    /** Falla #2 (revisión a 2 años): sin esto, REALIZADA era inalcanzable. */
    @PutMapping("/{id}/realizar")
    public ReservaResponse realizar(@AuthenticationPrincipal AuthPrincipal principal,
                                    @PathVariable Long id) {
        return reservaService.realizar(principal.tenantId(), principal.usuarioId(), id);
    }

    @PutMapping("/{id}/cancelar")
    public ReservaResponse cancelar(@AuthenticationPrincipal AuthPrincipal principal,
                                    @PathVariable Long id,
                                    @RequestBody(required = false) CancelarRequest req) {
        return reservaService.cancelar(principal.tenantId(), principal.usuarioId(), id, req);
    }

    @PostMapping("/{id}/pagos")
    public ReservaResponse registrarPago(@AuthenticationPrincipal AuthPrincipal principal,
                                         @PathVariable Long id,
                                         @Valid @RequestBody PagoRequest req) {
        // registrado_por (falla #5, 2 años): auditoría de quién anotó el pago
        return reservaService.registrarPago(principal.tenantId(), principal.usuarioId(), id, req);
    }

    /** Descarga archivo .ics para añadir la reserva al calendario (Google/Apple/Outlook). */
    @GetMapping("/{id}/calendar/ics")
    public ResponseEntity<String> descargarIcs(@AuthenticationPrincipal AuthPrincipal principal,
                                               @PathVariable Long id) {
        String ics = calendarSyncService.generarIcs(principal.tenantId(), id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=reserva-" + id + ".ics")
                .contentType(MediaType.parseMediaType("text/calendar; charset=UTF-8"))
                .body(ics);
    }

    /** Link directo para añadir al Google Calendar (abre el navegador). */
    @GetMapping("/{id}/calendar/google-link")
    public ResponseEntity<java.util.Map<String, String>> googleCalendarLink(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id) {
        String link = calendarSyncService.generarGoogleCalendarLink(principal.tenantId(), id);
        return ResponseEntity.ok(java.util.Map.of("url", link));
    }
}
