package cl.reservakids.infrastructure.web;

import cl.reservakids.application.dto.AuthDtos.*;
import cl.reservakids.application.usecase.AuthService;
import cl.reservakids.infrastructure.security.AuthPrincipal;
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

    @PostMapping("/register")
    public ResponseEntity<TokenResponse> register(@Valid @RequestBody RegistroRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.registrar(req));
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req);
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest req) {
        return authService.refresh(req);
    }

    /**
     * Fix #6 (revisión de código): con el access token vencido (ruta permitAll → principal
     * null) el logout respondía 204 sin revocar nada. El body opcional permite revocar
     * por el hash del refresh token aunque la identidad JWT ya no sea válida.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthPrincipal principal,
                                       @RequestBody(required = false) RefreshRequest req) {
        authService.logout(principal == null ? null : principal.usuarioId(),
                req == null ? null : req.refreshToken());
        return ResponseEntity.noContent().build();
    }
}
