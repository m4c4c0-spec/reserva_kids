package cl.reservakids.infrastructure.web;

import cl.reservakids.infrastructure.adapter.NotificacionAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Falla #3 (revisión a 2 años): estado del canal de notificaciones, consultado por el panel.
 * Si el email lleva fallos consecutivos, el frontend muestra un aviso — sin esto la
 * degradación "elegante" del SMTP era invisible y el dueño perdía solicitudes en silencio.
 */
@RestController
@RequestMapping("/api/sistema")
@RequiredArgsConstructor
public class SistemaController {

    private final NotificacionAdapter notificacionAdapter;

    @GetMapping("/notificaciones")
    public NotificacionAdapter.EstadoEnvios estadoNotificaciones() {
        return notificacionAdapter.estadoEnvios();
    }
}
