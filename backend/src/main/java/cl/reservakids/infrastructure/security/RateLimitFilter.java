package cl.reservakids.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    @Value("${app.rate-limit.public-max-por-minuto:30}")
    private int publicMaxPorMinuto;

    @Value("${app.rate-limit.auth-max-por-minuto:10}")
    private int authMaxPorMinuto;

    @Value("${app.rate-limit.cliente-auth-max-por-minuto:10}")
    private int clienteAuthMaxPorMinuto;

    @Value("${app.rate-limit.admin-auth-max-por-minuto:5}")
    private int adminAuthMaxPorMinuto;

    @Value("${app.rate-limit.panel-max-por-minuto:60}")
    private int panelMaxPorMinuto;

    @Value("${app.rate-limit.webhook-max-por-minuto:5}")
    private int webhookMaxPorMinuto;

    @Value("${app.security.trust-proxy}")
    private boolean trustProxy;

    private record Ventana(long epochMinuto, AtomicInteger contador, int maxPermitido) {}

    private final Map<String, Ventana> ventanas = new ConcurrentHashMap<>();

    private long ultimaLimpieza = 0;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri.startsWith("/actuator/")) {
            return true;
        }
        return !uri.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String ip = ipCliente(request);
        int maxPorMinuto = resolverLimite(request);
        if (maxPorMinuto <= 0) {
            chain.doFilter(request, response);
            return;
        }

        long minutoActual = Instant.now().getEpochSecond() / 60;

        Ventana ventana = ventanas.compute(ip, (k, v) -> {
            if (v == null || v.epochMinuto() != minutoActual || v.maxPermitido() != maxPorMinuto) {
                return new Ventana(minutoActual, new AtomicInteger(), maxPorMinuto);
            }
            return v;
        });

        int contador = ventana.contador().incrementAndGet();
        if (contador > maxPorMinuto) {
            int segundosRestantes = 60 - (int) (Instant.now().getEpochSecond() % 60);
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(Math.max(segundosRestantes, 1)));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"Demasiadas solicitudes, intenta en un minuto\"}");
            return;
        }

        limpiarPeriodicamente(minutoActual);
        chain.doFilter(request, response);
    }

    private int resolverLimite(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri.startsWith("/api/public/webhooks/")) {
            return webhookMaxPorMinuto;
        }
        if (uri.startsWith("/api/admin-auth/")) {
            return adminAuthMaxPorMinuto;
        }
        if (uri.startsWith("/api/auth/")) {
            return authMaxPorMinuto;
        }
        if (uri.startsWith("/api/cliente-auth/")) {
            return clienteAuthMaxPorMinuto;
        }
        if (uri.startsWith("/api/public/")) {
            return publicMaxPorMinuto;
        }
        return panelMaxPorMinuto;
    }

    private void limpiarPeriodicamente(long minutoActual) {
        long ahora = System.currentTimeMillis();
        if (ahora - ultimaLimpieza < 60_000) {
            return;
        }
        ultimaLimpieza = ahora;
        ventanas.entrySet().removeIf(e -> {
            long edad = minutoActual - e.getValue().epochMinuto();
            return edad > 2;
        });
    }

    private String ipCliente(HttpServletRequest request) {
        if (trustProxy) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                String[] ips = forwarded.split(",");
                return ips[ips.length - 1].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
