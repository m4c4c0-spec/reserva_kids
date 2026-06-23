package cl.reservakids.infrastructure.security;

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
import java.util.List;
import java.util.Set;

/**
 * Triple control CSRF para endpoints que usan cookies (auth refresh/logout).
 *
 * <h3>Control PREVENTIVO</h3>
 * Exige {@code X-Requested-With: XMLHttpRequest} — un atacante no puede setear
 * este header cross-origen sin que el navegador realice un preflight CORS,
 * que {@link SecurityConfig} rechaza para orígenes no permitidos.
 *
 * <h3>Control DETECTIVO</h3>
 * Valida el header {@code Origin} (o {@code Referer}) contra el origen del frontend
 * configurado. Si no coinciden, se rechaza y se loguea el intento con IP y User-Agent
 * para detección de ataques.
 *
 * <h3>Alcance</h3>
 * Solo se aplica a endpoints que procesan cookies (refresh y logout de los 3 actores).
 * El resto de la API usa Bearer token (inmune CSRF) o es pública (permitAll).
 */
@Slf4j
@Component
public class CsrfFilter extends OncePerRequestFilter {

    private static final Set<String> RUTAS_PROTEGIDAS = Set.of(
            "/api/auth/refresh", "/api/auth/logout",
            "/api/cliente-auth/refresh", "/api/cliente-auth/logout",
            "/api/admin-auth/refresh", "/api/admin-auth/logout");

    @Value("${app.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return RUTAS_PROTEGIDAS.stream().noneMatch(path::equals);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // ── Control DETECTIVO: validar Origin/Referer ──
        String origin = request.getHeader("Origin");
        String referer = request.getHeader("Referer");

        if (origin != null && !origin.isBlank()) {
            if (!esOrigenPermitido(origin)) {
                log.warn("CSRF bloqueado por Origin inválido: origin={} ip={} ua={} path={}",
                        origin, request.getRemoteAddr(), request.getHeader("User-Agent"),
                        request.getRequestURI());
                responderBloqueo(response);
                return;
            }
        } else if (referer != null && !referer.isBlank()) {
            // Fallback a Referer (menos fiable que Origin pero mejor que nada)
            if (!esOrigenPermitido(referer)) {
                log.warn("CSRF bloqueado por Referer inválido: referer={} ip={} ua={} path={}",
                        referer, request.getRemoteAddr(), request.getHeader("User-Agent"),
                        request.getRequestURI());
                responderBloqueo(response);
                return;
            }
        }
        // Si no hay ni Origin ni Referer (ej. tests, clientes CLI), se permite pasar
        // porque el siguiente control (X-Requested-With) es el más fuerte.

        // ── Control PREVENTIVO: exigir X-Requested-With ──
        String requestedWith = request.getHeader("X-Requested-With");
        if (!"XMLHttpRequest".equals(requestedWith)) {
            log.warn("CSRF bloqueado por falta de X-Requested-With: ip={} ua={} path={}",
                    request.getRemoteAddr(), request.getHeader("User-Agent"),
                    request.getRequestURI());
            responderBloqueo(response);
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean esOrigenPermitido(String originOrReferer) {
        String normalizada = originOrReferer.trim();
        if (normalizada.endsWith("/")) {
            normalizada = normalizada.substring(0, normalizada.length() - 1);
        }
        for (String permitido : allowedOrigins) {
            String p = permitido.trim();
            if (p.endsWith("/")) {
                p = p.substring(0, p.length() - 1);
            }
            if (normalizada.startsWith(p)) {
                return true;
            }
        }
        return false;
    }

    private void responderBloqueo(HttpServletResponse response) throws IOException {
        response.setStatus(403);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"status\":403,\"error\":\"Forbidden\",\"message\":\"Petición cross-site no permitida\"}");
    }
}
