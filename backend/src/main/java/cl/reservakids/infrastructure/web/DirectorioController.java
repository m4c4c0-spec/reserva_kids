package cl.reservakids.infrastructure.web;

import cl.reservakids.application.dto.ClienteDtos.NegocioResumen;
import cl.reservakids.application.dto.ReservaDtos.ReservaResponse;
import cl.reservakids.application.usecase.DirectorioService;
import cl.reservakids.application.usecase.ReservaService;
import cl.reservakids.domain.model.EstadoReserva;
import cl.reservakids.infrastructure.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Área de cliente autenticado (rol CLIENTE). El directorio de negocios con disponibilidad
 * y el historial de reservas del apoderado (Sprint 2 §2.2).
 */
@RestController
@RequestMapping("/api/cliente")
@RequiredArgsConstructor
public class DirectorioController {

    private final DirectorioService directorioService;
    private final ReservaService reservaService;

    @GetMapping("/negocios")
    public List<NegocioResumen> negocios() {
        return directorioService.negociosConDisponibilidad();
    }

    /**
     * Sprint 2 §2.2: historial de reservas del cliente autenticado. El {@code cuentaClienteId}
     * sale del JWT (sub del token de cliente), nunca del request — el apoderado solo ve sus
     * propias citas, en cualquier negocio donde haya agendado.
     */
    @GetMapping("/reservas")
    public Page<ReservaResponse> reservas(@AuthenticationPrincipal AuthPrincipal principal,
                                          @RequestParam(required = false) EstadoReserva estado,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        return reservaService.listarReservasDeCliente(principal.usuarioId(), estado, pageable);
    }
}
