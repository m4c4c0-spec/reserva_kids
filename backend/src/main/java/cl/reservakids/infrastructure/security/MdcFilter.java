package cl.reservakids.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Inyecta un correlation ID (X-Request-ID) en el MDC de SLF4J para que todos los logs
 * de una misma request compartan el mismo identificador (RNF-07 trazabilidad).
 * Si el cliente ya envía uno, se respeta; si no, se genera.
 */
@Component
public class MdcFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Request-ID";
    private static final String KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String requestId = request.getHeader(HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        response.setHeader(HEADER, requestId);
        MDC.put(KEY, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(KEY);
        }
    }
}
