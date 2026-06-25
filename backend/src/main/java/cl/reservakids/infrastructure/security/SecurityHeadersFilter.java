package cl.reservakids.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Defensa en profundidad: encabezados HTTP de seguridad desde la API.
 * El Caddy en producción también los configura, pero si la API se expone directamente
 * (Cloud Run, VPS sin proxy) estos headers siguen activos.
 */
@Component
public class SecurityHeadersFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("X-XSS-Protection", "0"); // obsoleto pero desactiva el filtro XSS del navegador (redundante con CSP)
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains; preload");
        response.setHeader("X-Permitted-Cross-Domain-Policies", "none");
        response.setHeader("Cross-Origin-Resource-Policy", "same-origin");
        response.setHeader("Cross-Origin-Opener-Policy", "same-origin");
        // Este filtro solo corre para /api/* (ver shouldNotFilter): son respuestas JSON,
        // nunca HTML ni scripts. Por eso un CSP estricto 'none' es lo correcto (y coincide
        // con el bloque @api del Caddyfile). El CSP de las rutas SSR lo gestiona nuxt-security.
        response.setHeader("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'");
        chain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !uri.startsWith("/api/");
    }
}
