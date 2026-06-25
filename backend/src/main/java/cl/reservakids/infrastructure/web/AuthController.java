package cl.reservakids.infrastructure.web;

import cl.reservakids.application.dto.AuthDtos.*;
import cl.reservakids.application.usecase.AuthService;
import cl.reservakids.application.usecase.PasswordResetService;
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

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final RefreshCookieService refreshCookieService;
    private final OAuth2Service oauth2Service;

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
        String refresh = resolverRefresh(cookieRefresh, req);
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
        String refresh = resolverRefresh(cookieRefresh, req);
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
        passwordResetService.solicitarResetPassword(req.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reset/confirmar")
    public ResponseEntity<Void> confirmarReset(@Valid @RequestBody ResetConfirmacionRequest req) {
        passwordResetService.confirmarResetPassword(req);
        return ResponseEntity.noContent().build();
    }

    /**
     * V27: pide un magic link de login sin contraseña por email. 204 SIEMPRE — anti-enumeración:
     * no revela si el email está registrado (igual que /reset/solicitar). Rate limit cubre /api/auth/**.
     */
    @PostMapping("/magic/solicitar")
    public ResponseEntity<Void> solicitarMagic(@Valid @RequestBody MagicSolicitudRequest req) {
        authService.solicitarMagicLink(req.email());
        return ResponseEntity.noContent().build();
    }

    /** V27: canjea el magic link — valida el token, emite access+refresh y setea la cookie. */
    @PostMapping("/magic/entrar")
    public TokenResponse entrarMagic(@Valid @RequestBody MagicEntradaRequest req, HttpServletResponse res) {
        TokenResponse tokens = authService.entrarConMagicLink(req.token());
        refreshCookieService.setear(res, RefreshCookieService.COOKIE_DUENO, tokens.refreshToken());
        return sinRefresh(tokens);
    }

    /** OAuth2/SSO: obtiene la URL de autorización para el proveedor indicado. */
    @GetMapping("/oauth2/{provider}/authorize")
    public ResponseEntity<OAuth2AuthorizeResponse> oauth2Authorize(
            @PathVariable String provider,
            @RequestParam(defaultValue = "dueno") String type) {
        String url = oauth2Service.buildAuthorizeUrl(provider, type);
        if (url == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(new OAuth2AuthorizeResponse(url));
    }

    /** OAuth2/SSO: canjea el código del proveedor por tokens de la app. */
    @PostMapping("/oauth2/{provider}")
    public TokenResponse oauth2Login(
            @PathVariable String provider,
            @Valid @RequestBody OAuth2LoginRequest req,
            HttpServletResponse res) {
        oauth2Service.verifyState(req.state(), "dueno", provider);
        TokenResponse tokens = authService.oauth2Login(
                provider, req.code(), req.redirectUri(), req.nombreNegocio(), req.slug());
        refreshCookieService.setear(res, RefreshCookieService.COOKIE_DUENO, tokens.refreshToken());
        return sinRefresh(tokens);
    }

    /** Completa el registro OAuth2 de un dueño luego de proveer nombre y slug. */
    @PostMapping("/oauth2/complete")
    public TokenResponse oauth2Complete(
            @Valid @RequestBody OAuth2CompleteRequest req,
            HttpServletResponse res) {
        TokenResponse tokens = authService.completarRegistroOAuth2(req.pendingToken(), req.nombreNegocio(), req.slug());
        refreshCookieService.setear(res, RefreshCookieService.COOKIE_DUENO, tokens.refreshToken());
        return sinRefresh(tokens);
    }

    /** Refresh token: prioriza la cookie HttpOnly; cae al body para clientes no-navegador. */
    private static String resolverRefresh(String cookieRefresh, RefreshRequest req) {
        if (cookieRefresh != null && !cookieRefresh.isBlank()) {
            return cookieRefresh;
        }
        return req != null ? req.refreshToken() : null;
    }

    /** El refresh token vive solo en la cookie HttpOnly; el body no lo expone. */
    private static TokenResponse sinRefresh(TokenResponse t) {
        return new TokenResponse(t.accessToken(), null, t.slug(), t.nombreNegocio());
    }
}
