package cl.reservakids.infrastructure.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Endpoint de Real User Monitoring (RUM).
 * Recibe beacons de Web Vitals (LCP, FCP, CLS, TTFB) desde el frontend vía
 * {@code navigator.sendBeacon}. Fire-and-forget: el frontend no espera respuesta.
 *
 * En producción, estos datos se pueden reenviar a un sistema de analytics
 * (plausible, posthog, google analytics 4). Por ahora se loguean para debugging.
 */
@Slf4j
@RestController
public class RumController {

    @PostMapping("/api/public/rum")
    public ResponseEntity<Void> recibirMetica(@RequestBody(required = false) Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return ResponseEntity.ok().build();
        }
        String nombre = String.valueOf(payload.getOrDefault("nombre", "unknown"));
        Object valor = payload.get("valor");
        log.debug("RUM {} = {} (ts={})", nombre, valor, payload.get("ts"));
        return ResponseEntity.ok().build();
    }
}
