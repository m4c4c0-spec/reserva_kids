package cl.reservakids.infrastructure.security;

import cl.reservakids.domain.repository.RateLimitBucketRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

@Slf4j
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

    private final RateLimitBucketRepository bucketRepository;

    private volatile long ultimaLimpieza = 0;

    public RateLimitFilter(RateLimitBucketRepository bucketRepository) {
        this.bucketRepository = bucketRepository;
    }

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
        String rutaTipo = resolverTipoRuta(request);
        if (maxPorMinuto <= 0) {
            chain.doFilter(request, response);
            return;
        }

        long minutoActual = Instant.now().getEpochSecond() / 60;

        int contador;
        try {
            // H2: conteo ATÓMICO en la BD (compartido entre instancias), no en memoria local.
            contador = bucketRepository.incrementarYContar(ip, rutaTipo, minutoActual, maxPorMinuto);
        } catch (Exception e) {
            // Fail-open: un fallo de BD no debe tumbar la API. Se loguea y se permite la request;
            // preferimos disponibilidad a bloquear todo el tráfico por el rate limiter.
            log.warn("Rate limit no disponible (BD), permitiendo la request (fail-open): {}", e.getMessage());
            chain.doFilter(request, response);
            return;
        }

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

    private String resolverTipoRuta(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri.startsWith("/api/public/webhooks/")) return "webhook";
        if (uri.startsWith("/api/admin-auth/")) return "admin-auth";
        if (uri.startsWith("/api/auth/")) return "auth";
        if (uri.startsWith("/api/cliente-auth/")) return "cliente-auth";
        if (uri.startsWith("/api/public/")) return "public";
        return "panel";
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
        try {
            // Borra buckets de ventanas pasadas (>2 min) para que la tabla no acumule IPs inactivas.
            bucketRepository.purgarExpirados(minutoActual - 2);
        } catch (Exception e) {
            log.debug("No se pudo purgar rate limit buckets: {}", e.getMessage());
        }
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
