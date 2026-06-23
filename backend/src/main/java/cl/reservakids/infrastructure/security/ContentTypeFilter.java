package cl.reservakids.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Rechaza peticiones con Content-Type distinto de application/json en endpoints
 * mutantes (POST/PUT/PATCH).
 *
 * <h3>Motivación</h3>
 * Un atacante podría intentar CSRF con form enctype (multipart/form-data o
 * application/x-www-form-urlencoded) en endpoints que esperan JSON. Al exigir
 * Content-Type: application/json forzamos un preflight CORS que el
 * {@link SecurityConfig} rechaza si el origen no es el del frontend.
 *
 * <h3>Exclusiones</h3>
 * No se aplica a GET/DELETE/OPTIONS/HEAD, ni a webhooks (MP/Khipu pueden enviar
 * otros Content-Type), ni a endpoints sin body (POST sin body = ok sin Content-Type).
 */
@Slf4j
@Component
public class ContentTypeFilter extends OncePerRequestFilter {

    private static final Set<String> METODOS_MUTANTES = Set.of("POST", "PUT", "PATCH");
    private static final String MEDIA_TYPE_JSON = "application/json";
    private static final String WEBHOOK_PREFIX = "/api/public/webhooks/";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!METODOS_MUTANTES.contains(request.getMethod().toUpperCase())) {
            return true;
        }
        String uri = request.getRequestURI();
        return !uri.startsWith("/api/") || uri.startsWith(WEBHOOK_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String contentType = request.getContentType();
        if (contentType == null || contentType.isBlank()) {
            // Exclusión documentada: un POST/PUT/PATCH sin body (ej. /suspender,
            // /reactivar, acciones idempotentes) es legítimo sin Content-Type. El
            // vector CSRF que este filtro mitiga (form enctype) siempre lleva body,
            // así que una petición sin cuerpo no es sospechosa.
            if (request.getContentLengthLong() <= 0) {
                chain.doFilter(request, response);
                return;
            }
            log.warn("Content-Type ausente en mutación: method={} uri={} ip={}",
                    request.getMethod(), request.getRequestURI(), request.getRemoteAddr());
            response.setStatus(415);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"status\":415,\"error\":\"Unsupported Media Type\",\"message\":\"Content-Type application/json requerido\"}");
            return;
        }

        // Extraer el MIME type base (ignorar charset, boundary, etc.)
        String mimeType = contentType.split(";")[0].trim().toLowerCase();
        if (!MEDIA_TYPE_JSON.equals(mimeType)) {
            log.warn("Content-Type no permitido en mutación: type={} method={} uri={} ip={}",
                    contentType, request.getMethod(), request.getRequestURI(), request.getRemoteAddr());
            response.setStatus(415);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"status\":415,\"error\":\"Unsupported Media Type\",\"message\":\"Content-Type application/json requerido\"}");
            return;
        }

        chain.doFilter(request, response);
    }
}
