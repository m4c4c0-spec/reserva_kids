package cl.reservakids.infrastructure.web;

import cl.reservakids.application.dto.AdminDtos.*;
import cl.reservakids.application.dto.AuthDtos;
import cl.reservakids.application.usecase.AdminAuthService;
import cl.reservakids.infrastructure.oauth2.OAuth2Service;
import cl.reservakids.infrastructure.security.AuthPrincipal;
import cl.reservakids.infrastructure.security.RefreshCookieService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Auth del administrador de plataforma. Público + rate-limited; sin auto-registro. */
@RestController
@RequestMapping("/api/admin-auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService adminAuthService;
    private final RefreshCookieService refreshCookieService;
    private final OAuth2Service oauth2Service;

    @PostMapping("/login")
    public AdminTokenResponse login(@Valid @RequestBody LoginAdminRequest req, HttpServletResponse res) {
        AdminTokenResponse tokens = adminAuthService.login(req);
        refreshCookieService.setear(res, RefreshCookieService.COOKIE_ADMIN, tokens.refreshToken());
        return sinRefresh(tokens);
    }

    @PostMapping("/refresh")
    public AdminTokenResponse refresh(@CookieValue(name = RefreshCookieService.COOKIE_ADMIN, required = false) String cookieRefresh,
                                      @RequestBody(required = false) AuthDtos.RefreshRequest req,
                                      HttpServletResponse res) {
        String refresh = resolverRefresh(cookieRefresh, req);
        if (refresh == null || refresh.isBlank()) {
            throw new BadCredentialsException("Refresh token inválido o expirado");
        }
        AdminTokenResponse tokens = adminAuthService.refresh(refresh);
        refreshCookieService.setear(res, RefreshCookieService.COOKIE_ADMIN, tokens.refreshToken());
        return sinRefresh(tokens);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthPrincipal principal,
                                       @CookieValue(name = RefreshCookieService.COOKIE_ADMIN, required = false) String cookieRefresh,
                                       @RequestBody(required = false) AuthDtos.RefreshRequest req,
                                       HttpServletResponse res) {
        String refresh = resolverRefresh(cookieRefresh, req);
        adminAuthService.logout(principal == null ? null : principal.usuarioId(), refresh);
        refreshCookieService.borrar(res, RefreshCookieService.COOKIE_ADMIN);
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
    private static AdminTokenResponse sinRefresh(AdminTokenResponse t) {
        return new AdminTokenResponse(t.accessToken(), null, t.email(), t.nombre());
    }

    /** OAuth2/SSO: obtiene la URL de autorización para el proveedor indicado. */
    @GetMapping("/oauth2/{provider}/authorize")
    public ResponseEntity<AuthDtos.OAuth2AuthorizeResponse> oauth2Authorize(
            @PathVariable String provider) {
        String url = oauth2Service.buildAuthorizeUrl(provider, "admin");
        if (url == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(new AuthDtos.OAuth2AuthorizeResponse(url));
    }

    /** OAuth2/SSO: canjea el código del proveedor por tokens de admin. */
    @PostMapping("/oauth2/{provider}")
    public AdminTokenResponse oauth2Login(
            @PathVariable String provider,
            @Valid @RequestBody AuthDtos.OAuth2Request req,
            HttpServletResponse res) {
        oauth2Service.verifyState(req.state(), "admin", provider);
        AdminTokenResponse tokens = adminAuthService.oauth2Login(provider, req.code(), req.redirectUri());
        refreshCookieService.setear(res, RefreshCookieService.COOKIE_ADMIN, tokens.refreshToken());
        return sinRefresh(tokens);
    }
}
