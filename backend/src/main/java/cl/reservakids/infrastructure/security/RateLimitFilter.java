package cl.reservakids.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * RNF-02: rate limiting simple por IP en endpoints públicos y de auth.
 * Ventana fija en memoria — suficiente para una instancia; migrar a bucket4j/Redis si se escala.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_POR_MINUTO = 30;

    /**
     * Detrás de un reverse proxy (Caddy/nginx), getRemoteAddr() es la IP del proxy:
     * todos los clientes compartirían un único bucket y el sitio público se bloquearía
     * con 429 al primer pico. Activar TRUST_PROXY=true SOLO si el proxy setea X-Forwarded-For.
     */
    @org.springframework.beans.factory.annotation.Value("${app.security.trust-proxy}")
    private boolean trustProxy;

    private record Ventana(long epochMinuto, AtomicInteger contador) {}

    private final Map<String, Ventana> ventanas = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !(uri.startsWith("/api/public/") || uri.startsWith("/api/auth/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String ip = ipCliente(request);
        long minutoActual = Instant.now().getEpochSecond() / 60;

        Ventana ventana = ventanas.compute(ip, (k, v) ->
                (v == null || v.epochMinuto() != minutoActual)
                        ? new Ventana(minutoActual, new AtomicInteger())
                        : v);

        if (ventana.contador().incrementAndGet() > MAX_POR_MINUTO) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"Demasiadas solicitudes, intenta en un minuto\"}");
            return;
        }
        if (ventanas.size() > 10_000) {
            ventanas.entrySet().removeIf(e -> e.getValue().epochMinuto() != minutoActual);
        }
        chain.doFilter(request, response);
    }

    private String ipCliente(HttpServletRequest request) {
        if (trustProxy) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                // Fix #1 (revisión de código): la PRIMERA IP es falsificable — el cliente puede
                // enviar su propio X-Forwarded-For y el proxy solo appendea la real al final.
                // Tomar la primera permitía estrenar un bucket por request (bypass total del
                // rate limit). La ÚLTIMA es la única añadida por el proxy de confianza.
                String[] ips = forwarded.split(",");
                return ips[ips.length - 1].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
