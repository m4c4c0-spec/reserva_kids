package cl.reservakids.application.usecase;

import cl.reservakids.application.dto.AdminDtos.*;
import cl.reservakids.domain.model.Administrador;
import cl.reservakids.domain.model.AuthEvent;
import cl.reservakids.domain.model.RefreshTokenAdmin;
import cl.reservakids.domain.repository.AdministradorRepository;
import cl.reservakids.domain.repository.RefreshTokenAdminRepository;
import cl.reservakids.infrastructure.oauth2.OAuth2UserInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

/**
 * Auth del administrador de plataforma. Espejo de {@link ClienteAuthService} (reusa BCrypt,
 * JWT y el mismo modelo de refresh con rotación), con dos diferencias deliberadas:
 *   - NO hay auto-registro público: el primer admin se bootstrapea por env ({@link AdminBootstrap}).
 *   - NO hay reset de contraseña por email: un admin es operación, no usuario masivo (se rota
 *     por env o, más adelante, por un endpoint admin→admin).
 * Emite tokens con rol ADMIN y sin tenant.
 */
@Service
@RequiredArgsConstructor
public class AdminAuthService {

    private final AdministradorRepository administradorRepository;
    private final RefreshTokenAdminRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenPort tokenPort;
    private final AuthEventPort authEvent;
    private final OAuth2Port oauth2Port;
    private final AuthCrypto authCrypto;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.jwt.refresh-days}")
    private long refreshDays;

    private static String normalizarEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional
    public AdminTokenResponse login(LoginAdminRequest req) {
        String email = normalizarEmail(req.email());
        Administrador admin = administradorRepository.findByEmail(email)
                .orElseThrow(() -> {
                    authEvent.registrar(AuthEvent.ACTOR_ADMIN, null, email,
                            AuthEvent.LOGIN_FAIL, AuthEvent.FAILURE, "Email no registrado");
                    return new BadCredentialsException("Credenciales inválidas");
                });
        if (admin.isOauth() && admin.getPasswordHash() == null) {
            authEvent.registrar(AuthEvent.ACTOR_ADMIN, admin.getId(), email,
                    AuthEvent.LOGIN_FAIL, AuthEvent.FAILURE, "Cuenta OAuth sin contraseña");
            throw new BadCredentialsException(
                    "Esta cuenta usa inicio de sesión con " + admin.getOauthProvider()
                    + ". Por favor inicia sesión con ese proveedor.");
        }
        if (!passwordEncoder.matches(req.password(), admin.getPasswordHash())) {
            authEvent.registrar(AuthEvent.ACTOR_ADMIN, admin.getId(), email,
                    AuthEvent.LOGIN_FAIL, AuthEvent.FAILURE, "Contraseña incorrecta");
            throw new BadCredentialsException("Credenciales inválidas");
        }
        authEvent.registrar(AuthEvent.ACTOR_ADMIN, admin.getId(), email,
                AuthEvent.LOGIN, AuthEvent.SUCCESS, null);
        return emitirTokens(admin);
    }

    /**
     * Refresh con rotación: el token usado se revoca; reusar un token ya rotado revoca
     * toda la familia (señal de robo). Mismo comportamiento que dueño/cliente.
     */
    @Transactional(noRollbackFor = BadCredentialsException.class)
    public AdminTokenResponse refresh(String refreshTokenPlano) {
        RefreshTokenAdmin actual = refreshTokenRepository.findByTokenHash(authCrypto.hashToken(refreshTokenPlano))
                .orElseThrow(() -> new BadCredentialsException("Refresh token inválido o expirado"));
        if (actual.isRevocado()) {
            refreshTokenRepository.revocarTodosDeAdmin(actual.getAdministradorId());
            authEvent.registrar(AuthEvent.ACTOR_ADMIN, actual.getAdministradorId(),
                    "id:" + actual.getAdministradorId(),
                    AuthEvent.THEFT_DETECTED, AuthEvent.FAILURE,
                    "Token ya rotado reusado — posible robo, sesiones revocadas");
            throw new BadCredentialsException("Refresh token inválido o expirado");
        }
        if (!actual.getExpiraEn().isAfter(OffsetDateTime.now())) {
            throw new BadCredentialsException("Refresh token inválido o expirado");
        }
        actual.setRevocado(true);

        Administrador admin = administradorRepository.findById(actual.getAdministradorId())
                .orElseThrow(() -> new BadCredentialsException("Administrador no encontrado"));
        authEvent.registrar(AuthEvent.ACTOR_ADMIN, admin.getId(), admin.getEmail(),
                AuthEvent.REFRESH, AuthEvent.SUCCESS, null);
        return emitirTokens(admin);
    }

    @Transactional
    public void logout(Long adminId, String refreshTokenPlano) {
        if (adminId != null) {
            refreshTokenRepository.revocarTodosDeAdmin(adminId);
            authEvent.registrar(AuthEvent.ACTOR_ADMIN, adminId, "id:" + adminId,
                    AuthEvent.LOGOUT, AuthEvent.SUCCESS, null);
        }
        if (refreshTokenPlano != null && !refreshTokenPlano.isBlank()) {
            refreshTokenRepository.findByTokenHash(authCrypto.hashToken(refreshTokenPlano))
                    .ifPresent(t -> refreshTokenRepository.revocarTodosDeAdmin(t.getAdministradorId()));
        }
    }

    private AdminTokenResponse emitirTokens(Administrador admin) {
        String access = tokenPort.emitirAccessTokenAdmin(admin.getId(), admin.getEmail());
        String refreshPlano = generarTokenPlano();

        RefreshTokenAdmin refresh = new RefreshTokenAdmin();
        refresh.setId(UUID.randomUUID());
        refresh.setAdministradorId(admin.getId());
        refresh.setTokenHash(authCrypto.hashToken(refreshPlano));
        refresh.setExpiraEn(OffsetDateTime.now().plusDays(refreshDays));
        refreshTokenRepository.save(refresh);

        return new AdminTokenResponse(access, refreshPlano, admin.getEmail(), admin.getNombre());
    }

    private String generarTokenPlano() {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Transactional
    public AdminTokenResponse oauth2Login(String provider, String code, String redirectUri) {
        OAuth2UserInfo info = oauth2Port.verify(provider, code, redirectUri);
        String email = normalizarEmail(info.email());

        var existingByProvider = administradorRepository.findByOauthProviderAndOauthProviderId(
                info.provider(), info.providerId());
        if (existingByProvider.isPresent()) {
            Administrador admin = existingByProvider.get();
            authEvent.registrar(AuthEvent.ACTOR_ADMIN, admin.getId(), email,
                    AuthEvent.LOGIN, AuthEvent.SUCCESS, "OAuth2 " + provider);
            return emitirTokens(admin);
        }

        var existingByEmail = administradorRepository.findByEmail(email);
        if (existingByEmail.isPresent()) {
            Administrador admin = existingByEmail.get();
            admin.setOauthProvider(provider);
            admin.setOauthProviderId(info.providerId());
            authEvent.registrar(AuthEvent.ACTOR_ADMIN, admin.getId(), email,
                    AuthEvent.LOGIN, AuthEvent.SUCCESS, "OAuth2 " + provider + " vinculado");
            return emitirTokens(admin);
        }

        throw new org.springframework.security.authentication.BadCredentialsException(
                "No existe un administrador con ese email");
    }
}
