package cl.reservakids.infrastructure.web;

import cl.reservakids.application.dto.AuthDtos;
import cl.reservakids.application.dto.ClienteDtos.*;
import cl.reservakids.application.usecase.ClienteAuthService;
import cl.reservakids.domain.model.EstadoReserva;
import cl.reservakids.domain.repository.ClienteRepository;
import cl.reservakids.domain.repository.CuentaClienteRepository;
import cl.reservakids.domain.repository.ReservaRepository;
import cl.reservakids.infrastructure.oauth2.OAuth2Service;
import cl.reservakids.infrastructure.security.AuthPrincipal;
import cl.reservakids.infrastructure.security.RefreshCookieService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Registro/login de cuentas de cliente (apoderados). Público + rate-limited. */
@RestController
@RequestMapping("/api/cliente-auth")
@RequiredArgsConstructor
public class ClienteAuthController {

    private final ClienteAuthService clienteAuthService;
    private final RefreshCookieService refreshCookieService;
    private final OAuth2Service oauth2Service;
    private final CuentaClienteRepository cuentaClienteRepository;
    private final ClienteRepository clienteRepository;
    private final ReservaRepository reservaRepository;

    // ── Ley 21.719: acceso y supresión de datos personales del cliente ──

    /** El cliente autenticado consulta sus datos personales almacenados. */
    @GetMapping("/mis-datos")
    public ResponseEntity<?> misDatos(@AuthenticationPrincipal AuthPrincipal principal) {
        var cuenta = cuentaClienteRepository.findById(principal.usuarioId()).orElse(null);
        if (cuenta == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of(
                "nombre", cuenta.getNombre() != null ? cuenta.getNombre() : "",
                "email", cuenta.getEmail(),
                "telefono", cuenta.getTelefono() != null ? cuenta.getTelefono() : "",
                "rut", cuenta.getRut() != null ? cuenta.getRut() : ""));
    }

    /**
     * Ley 21.719: supresión de datos a demanda del propio cliente.
     * Anonimiza todos sus registros de Cliente en los negocios donde reservó.
     * Es idempotente: pedirlo 2 veces no es error.
     */
    @PostMapping("/suprimir-datos")
    public ResponseEntity<Map<String, String>> suprimirDatos(@AuthenticationPrincipal AuthPrincipal principal) {
        var cuenta = cuentaClienteRepository.findById(principal.usuarioId()).orElse(null);
        if (cuenta == null) return ResponseEntity.notFound().build();
        int anonimizados = 0;
        String telefono = cuenta.getTelefono();
        if (telefono != null && !telefono.isBlank()) {
            var clientes = clienteRepository.findAll().stream()
                    .filter(c -> telefono.equals(c.getTelefono()) && !c.isAnonimizado())
                    .toList();
            for (var c : clientes) {
                if (!reservaRepository.existsByClienteIdAndEstadoIn(c.getId(), EstadoReserva.ACTIVOS)) {
                    c.anonimizar(java.time.OffsetDateTime.now());
                    anonimizados++;
                }
            }
        }
        return ResponseEntity.ok(Map.of("mensaje",
                "Tus datos personales han sido suprimidos (" + anonimizados + " registros). " +
                "Las reservas históricas se conservan sin datos identificativos."));
    }

    @PostMapping("/register")
    public ResponseEntity<ClienteTokenResponse> register(@Valid @RequestBody RegistroClienteRequest req,
                                                         HttpServletResponse res) {
        ClienteTokenResponse tokens = clienteAuthService.registrar(req);
        refreshCookieService.setear(res, RefreshCookieService.COOKIE_CLIENTE, tokens.refreshToken());
        return ResponseEntity.status(HttpStatus.CREATED).body(sinRefresh(tokens));
    }

    @PostMapping("/login")
    public ClienteTokenResponse login(@Valid @RequestBody LoginClienteRequest req, HttpServletResponse res) {
        ClienteTokenResponse tokens = clienteAuthService.login(req);
        refreshCookieService.setear(res, RefreshCookieService.COOKIE_CLIENTE, tokens.refreshToken());
        return sinRefresh(tokens);
    }

    /**
     * V16: refresh con rotación para cuentas de cliente. El access dura 15 min;
     * el refresh (cookie HttpOnly) permite mantener la sesión sin volver a pedir contraseña.
     */
    @PostMapping("/refresh")
    public ClienteTokenResponse refresh(@CookieValue(name = RefreshCookieService.COOKIE_CLIENTE, required = false) String cookieRefresh,
                                        @RequestBody(required = false) AuthDtos.RefreshRequest req,
                                        HttpServletResponse res) {
        String refresh = resolverRefresh(cookieRefresh, req);
        if (refresh == null || refresh.isBlank()) {
            throw new org.springframework.security.authentication.BadCredentialsException(
                    "Refresh token inválido o expirado");
        }
        ClienteTokenResponse tokens = clienteAuthService.refresh(refresh);
        refreshCookieService.setear(res, RefreshCookieService.COOKIE_CLIENTE, tokens.refreshToken());
        return sinRefresh(tokens);
    }

    /**
     * V16: logout del cliente. El access puede estar vencido, por eso se revoca
     * también por el hash del refresh token (cookie HttpOnly o body opcional).
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthPrincipal principal,
                                       @CookieValue(name = RefreshCookieService.COOKIE_CLIENTE, required = false) String cookieRefresh,
                                       @RequestBody(required = false) AuthDtos.RefreshRequest req,
                                       HttpServletResponse res) {
        String refresh = resolverRefresh(cookieRefresh, req);
        clienteAuthService.logout(principal == null ? null : principal.usuarioId(), refresh);
        refreshCookieService.borrar(res, RefreshCookieService.COOKIE_CLIENTE);
        return ResponseEntity.noContent().build();
    }

    /**
     * Sprint 2 §2.3: recuperación de contraseña de cliente (apoderado). 204 SIEMPRE —
     * no revela si el email existe (anti-enumeración); rate limit ya cubre /api/cliente-auth/**.
     */
    @PostMapping("/reset/solicitar")
    public ResponseEntity<Void> solicitarReset(@Valid @RequestBody AuthDtos.ResetSolicitudRequest req) {
        clienteAuthService.solicitarResetPassword(req.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reset/confirmar")
    public ResponseEntity<Void> confirmarReset(@Valid @RequestBody AuthDtos.ResetConfirmacionRequest req) {
        clienteAuthService.confirmarResetPassword(req.token(), req.nuevaPassword());
        return ResponseEntity.noContent().build();
    }

    /** OAuth2/SSO: obtiene la URL de autorización para el proveedor indicado. */
    @GetMapping("/oauth2/{provider}/authorize")
    public ResponseEntity<AuthDtos.OAuth2AuthorizeResponse> oauth2Authorize(
            @PathVariable String provider) {
        String url = oauth2Service.buildAuthorizeUrl(provider, "cliente");
        if (url == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(new AuthDtos.OAuth2AuthorizeResponse(url));
    }

    /** OAuth2/SSO: canjea el código del proveedor por tokens de cliente. */
    @PostMapping("/oauth2/{provider}")
    public ClienteTokenResponse oauth2Login(
            @PathVariable String provider,
            @Valid @RequestBody AuthDtos.OAuth2Request req,
            HttpServletResponse res) {
        oauth2Service.verifyState(req.state(), "cliente", provider);
        ClienteTokenResponse tokens = clienteAuthService.oauth2Login(provider, req.code(), req.redirectUri());
        refreshCookieService.setear(res, RefreshCookieService.COOKIE_CLIENTE, tokens.refreshToken());
        return sinRefresh(tokens);
    }

    /** Refresh token: prioriza la cookie HttpOnly; cae al body para clientes no-navegador. */
    private static String resolverRefresh(String cookieRefresh, AuthDtos.RefreshRequest req) {
        if (cookieRefresh != null && !cookieRefresh.isBlank()) {
            return cookieRefresh;
        }
        return req != null ? req.refreshToken() : null;
    }

    /** El refresh token vive solo en la cookie HttpOnly; el body no lo expone. */
    private static ClienteTokenResponse sinRefresh(ClienteTokenResponse t) {
        return new ClienteTokenResponse(t.accessToken(), null, t.email(), t.nombre(), t.telefono());
    }
}
