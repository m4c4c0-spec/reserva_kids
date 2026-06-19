package cl.reservakids.infrastructure.web;

import cl.reservakids.application.dto.AuthDtos.*;
import cl.reservakids.application.usecase.AuthService;
import cl.reservakids.infrastructure.security.AuthPrincipal;
import cl.reservakids.infrastructure.security.RefreshCookieService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshCookieService refreshCookieService;

    @PostMapping("/register")
    public ResponseEntity<TokenResponse> register(@Valid @RequestBody RegistroRequest req,
                                                  HttpServletResponse res) {
        TokenResponse tokens = authService.registrar(req);
        refreshCookieService.setear(res, RefreshCookieService.COOKIE_DUENO, tokens.refreshToken());
        return ResponseEntity.status(HttpStatus.CREATED).body(sinRefresh(tokens));
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest req, HttpServletResponse res) {
        TokenResponse tokens = authService.login(req);
        refreshCookieService.setear(res, RefreshCookieService.COOKIE_DUENO, tokens.refreshToken());
        return sinRefresh(tokens);
    }

    /**
     * Lee el refresh token de la cookie HttpOnly (prioridad) o, por compatibilidad, del body.
     * El refresh va en cookie para que un XSS no pueda leerlo; el body solo se acepta para
     * clientes no-navegador (tests, integraciones).
     */
    @PostMapping("/refresh")
    public TokenResponse refresh(@CookieValue(name = RefreshCookieService.COOKIE_DUENO, required = false) String cookieRefresh,
                                 @RequestBody(required = false) RefreshRequest req,
                                 HttpServletResponse res) {
        String refresh = cookieRefresh != null && !cookieRefresh.isBlank()
                ? cookieRefresh
                : (req != null ? req.refreshToken() : null);
        if (refresh == null || refresh.isBlank()) {
            throw new org.springframework.security.authentication.BadCredentialsException(
                    "Refresh token inválido o expirado");
        }
        TokenResponse tokens = authService.refresh(new RefreshRequest(refresh));
        refreshCookieService.setear(res, RefreshCookieService.COOKIE_DUENO, tokens.refreshToken());
        return sinRefresh(tokens);
    }

    /**
     * Fix #6 (revisión de código): con el access token vencido (ruta permitAll → principal
     * null) el logout respondía 204 sin revocar nada. El body opcional permite revocar
     * por el hash del refresh token aunque la identidad JWT ya no sea válida; la cookie
     * HttpOnly cubre el caso del navegador.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthPrincipal principal,
                                       @CookieValue(name = RefreshCookieService.COOKIE_DUENO, required = false) String cookieRefresh,
                                       @RequestBody(required = false) RefreshRequest req,
                                       HttpServletResponse res) {
        String refresh = cookieRefresh != null && !cookieRefresh.isBlank()
                ? cookieRefresh
                : (req != null ? req.refreshToken() : null);
        authService.logout(principal == null ? null : principal.usuarioId(), refresh);
        refreshCookieService.borrar(res, RefreshCookieService.COOKIE_DUENO);
        return ResponseEntity.noContent().build();
    }

    /**
     * Falla 1.3 (revisión a 5 años): recuperación de contraseña. 204 SIEMPRE —
     * no revela si el email existe (anti-enumeración); rate limit ya cubre /api/auth/**.
     */
    @PostMapping("/reset/solicitar")
    public ResponseEntity<Void> solicitarReset(@Valid @RequestBody ResetSolicitudRequest req) {
        authService.solicitarResetPassword(req.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reset/confirmar")
    public ResponseEntity<Void> confirmarReset(@Valid @RequestBody ResetConfirmacionRequest req) {
        authService.confirmarResetPassword(req);
        return ResponseEntity.noContent().build();
    }

    /** El refresh token vive solo en la cookie HttpOnly; el body no lo expone. */
    private static TokenResponse sinRefresh(TokenResponse t) {
        return new TokenResponse(t.accessToken(), null, t.slug(), t.nombreNegocio());
    }
}
