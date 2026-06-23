package cl.reservakids.infrastructure.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final RateLimitFilter rateLimitFilter;
    private final IdempotencyFilter idempotencyFilter;
    private final ContentTypeFilter contentTypeFilter;
    private final MdcFilter mdcFilter;
    private final SecurityHeadersFilter securityHeadersFilter;
    private final CsrfFilter csrfFilter;

    @Value("${app.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Value("${app.security.bcrypt-strength:12}")
    private int bcryptStrength;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(bcryptStrength); // RNF-02: bcrypt ≥12 rounds (OWASP 2023+)
    }

    @Bean
    public MethodSecurityExpressionHandler methodSecurityExpressionHandler(PermissionEvaluator evaluator) {
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
        handler.setPermissionEvaluator(evaluator);
        return handler;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                // CSRF deshabilitado de forma segura (Sonar java:S4502 revisado):
                //  - Acceso autenticado por Bearer token en la cabecera Authorization: un sitio de
                //    terceros no puede leerlo ni adjuntarlo, así que no hay vector CSRF clásico.
                //  - Sesión STATELESS: no existe cookie de sesión de Spring que falsificar.
                //  - Las únicas cookies son los refresh tokens (rk_refresh / rk_cliente_refresh),
                //    HttpOnly + SameSite (RefreshCookieService, app.cookies.same-site=Lax): el
                //    navegador no las envía en peticiones cross-site, neutralizando CSRF en /refresh.
                //  - Endpoints JSON (Content-Type application/json) exigen preflight, que el CORS
                //    restrictivo (solo el origen del frontend) rechaza desde otros orígenes.
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/api/public/**", "/api/auth/**", "/api/cliente-auth/**", "/api/admin-auth/**", "/api/staff/login").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/cliente/**").hasRole("CLIENTE")
                        // Staff: acceso por URL como defense-in-depth + @PreAuthorize granular en cada endpoint
                        .requestMatchers("/api/staff/**").hasRole("STAFF")
                        // Panel del negocio: dueño siempre tiene acceso total. Staff con permiso
                        // accede mediante @PreAuthorize("hasPermission(...)") en cada controlador.
                        .requestMatchers("/api/roles/**", "/api/personal/**").hasAnyRole("DUENO", "STAFF")
                        .requestMatchers("/api/**").hasRole("DUENO")
                        .anyRequest().denyAll())
                .addFilterBefore(securityHeadersFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(contentTypeFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(csrfFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(idempotencyFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(mdcFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /** RNF-02: CORS restrictivo — solo el origen del frontend. */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
