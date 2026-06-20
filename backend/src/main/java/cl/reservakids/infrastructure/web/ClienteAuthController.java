package cl.reservakids.infrastructure.web;

import cl.reservakids.application.dto.AuthDtos;
import cl.reservakids.application.dto.ClienteDtos.*;
import cl.reservakids.application.usecase.ClienteAuthService;
import cl.reservakids.infrastructure.security.AuthPrincipal;
import cl.reservakids.infrastructure.security.RefreshCookieService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Registro/login de cuentas de cliente (apoderados). Público + rate-limited. */
@RestController
@RequestMapping("/api/cliente-auth")
@RequiredArgsConstructor
public class ClienteAuthController {

    private final ClienteAuthService clienteAuthService;
    private final RefreshCookieService refreshCookieService;

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
