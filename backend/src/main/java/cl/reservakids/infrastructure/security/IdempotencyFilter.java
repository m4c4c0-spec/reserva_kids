package cl.reservakids.infrastructure.security;

import cl.reservakids.domain.model.IdempotencyKey;
import cl.reservakids.domain.repository.IdempotencyKeyRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

/**
 * Previene operaciones duplicadas en mutaciones (POST/PUT/PATCH) mediante
 * {@code X-Idempotency-Key}.
 *
 * <h3>Motivación</h3>
 * Un doble clic del usuario, un reintento automático del frontend tras timeout,
 * o un proxy que reenvía la misma petición pueden crear dos reservas para el mismo
 * bloque. Este filtro registra la clave de idempotencia en BD antes de procesar
 * y rechaza reenvíos con 409 Conflict.
 *
 * <h3>Ausencia de clave</h3>
 * Si el header no está presente se permite la petición normalmente (compatibilidad
 * hacia atrás). El frontend lo envía solo en endpoints críticos (crear reserva,
 * crear pago).
 *
 * <h3>TTL</h3>
 * Las claves expiran tras 24 h (purgado por {@code IdempotencyKeyCleanupJob}).
 * Formato esperado: UUID v4 (36 chars) generado por el frontend.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotencyFilter extends OncePerRequestFilter {

    private static final Set<String> METODOS_MUTANTES = Set.of("POST", "PUT", "PATCH");
    private static final String HEADER = "X-Idempotency-Key";

    private final IdempotencyKeyRepository idempotencyKeyRepository;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!METODOS_MUTANTES.contains(request.getMethod().toUpperCase())) {
            return true;
        }
        String header = request.getHeader(HEADER);
        if (header == null || header.isBlank()) {
            return true;
        }
        String uri = request.getRequestURI();
        return !uri.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String idempotencyKey = request.getHeader(HEADER).trim();

        if (!esUuidValido(idempotencyKey)) {
            response.setStatus(400);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"status\":400,\"error\":\"Bad Request\",\"message\":\"X-Idempotency-Key debe ser un UUID v4\"}");
            return;
        }

        String endpoint = request.getRequestURI();

        try {
            boolean existe = idempotencyKeyRepository.existsById(idempotencyKey);
            if (existe) {
                log.info("Idempotency key duplicada: key={} endpoint={}", idempotencyKey, endpoint);
                response.setStatus(409);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write(
                        "{\"status\":409,\"error\":\"Conflict\",\"message\":\"Solicitud ya procesada\"}");
                return;
            }

            idempotencyKeyRepository.save(new IdempotencyKey(idempotencyKey, endpoint));
            chain.doFilter(request, response);
        } catch (Exception e) {
            log.warn("Fallo de idempotencia (BD), permitiendo request (fail-open): {}", e.getMessage());
            chain.doFilter(request, response);
        }
    }

    private boolean esUuidValido(String key) {
        try {
            UUID.fromString(key);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
