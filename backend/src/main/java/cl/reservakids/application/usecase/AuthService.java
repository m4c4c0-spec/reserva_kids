package cl.reservakids.application.usecase;

import cl.reservakids.application.dto.AuthDtos.*;
import cl.reservakids.domain.model.AuthEvent;
import cl.reservakids.domain.model.PasswordResetToken;
import cl.reservakids.domain.model.RefreshToken;
import cl.reservakids.domain.model.Tenant;
import cl.reservakids.domain.model.Usuario;
import cl.reservakids.infrastructure.oauth2.OAuth2Service;
import cl.reservakids.infrastructure.oauth2.OAuth2UserInfo;
import cl.reservakids.domain.repository.PasswordResetTokenRepository;
import cl.reservakids.domain.repository.RefreshTokenRepository;
import cl.reservakids.domain.repository.TenantRepository;
import cl.reservakids.domain.repository.UsuarioRepository;
import cl.reservakids.domain.exception.OAuth2PendingRegistrationException;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;

/**
 * Autenticación del dueño: registro, login, refresh con rotación y logout. La recuperación
 * de contraseña vive en {@link PasswordResetService}; los helpers de hash/normalización en
 * {@link AuthCrypto}.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final TenantRepository tenantRepository;
    private final UsuarioRepository usuarioRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final HorarioAtencionService horarioAtencionService;
    private final RbacService rbacService;
    private final PasswordEncoder passwordEncoder;
    private final TokenPort tokenPort;
    private final AuthEventPort authEvent;
    private final NotificacionPort notificacion;
    private final OAuth2Service oauth2Service;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.magic-link.minutos:10}")
    private long magicLinkMinutos;

    @Value("${app.jwt.refresh-days}")
    private long refreshDays;

    /**
     * Rutas del propio frontend/API: si un negocio se registrara con uno de estos slugs,
     * su página pública (/{slug}) quedaría inaccesible para siempre.
     */
    private static final java.util.Set<String> SLUGS_RESERVADOS = java.util.Set.of(
            "login", "panel", "api", "admin", "auth", "www", "app",
            "static", "assets", "public", "docs", "ayuda", "soporte",
            "reset", // falla 1.3: ruta del frontend para recuperar contraseña
            "privacidad", // página legal de privacidad
            "clientes"); // área de cliente (login + directorio) — no puede ser un slug de negocio

    /** RF-01: registro de negocio (crea tenant + usuario dueño). */
    @Transactional
    public TokenResponse registrar(RegistroRequest req) {
        String email = AuthCrypto.normalizarEmail(req.email());
        if (SLUGS_RESERVADOS.contains(req.slug())) {
            throw new IllegalArgumentException("Ese slug está reservado, elige otro");
        }
        if (tenantRepository.existsBySlug(req.slug())) {
            throw new IllegalArgumentException("El slug ya está en uso");
        }
        if (usuarioRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("El email ya está registrado");
        }
        Tenant tenant = new Tenant();
        tenant.setNombre(req.nombreNegocio());
        tenant.setSlug(req.slug());
        tenant = tenantRepository.save(tenant);

        // El agendamiento por hora requiere horario; sin esto el negocio nuevo no
        // aparecería en el directorio de clientes.
        horarioAtencionService.crearHorarioPorDefecto(tenant.getId());

        // RBAC: roles predefinidos para el personal del negocio nuevo
        rbacService.crearRolesPorDefecto(tenant.getId());

        Usuario usuario = new Usuario();
        usuario.setTenantId(tenant.getId());
        usuario.setEmail(email);
        usuario.setPasswordHash(passwordEncoder.encode(req.password()));
        usuarioRepository.save(usuario);

        authEvent.registrar(AuthEvent.ACTOR_DUENO, usuario.getId(), email,
                AuthEvent.LOGIN, AuthEvent.SUCCESS, "Registro de nuevo negocio: " + req.slug());
        return emitirTokens(usuario, tenant);
    }

    @Transactional
    public TokenResponse login(LoginRequest req) {
        String email = AuthCrypto.normalizarEmail(req.email());
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> {
                    authEvent.registrar(AuthEvent.ACTOR_DUENO, null, email,
                            AuthEvent.LOGIN_FAIL, AuthEvent.FAILURE, "Email no registrado");
                    return new BadCredentialsException("Credenciales inválidas");
                });
        if (usuario.isOauth() && usuario.getPasswordHash() == null) {
            authEvent.registrar(AuthEvent.ACTOR_DUENO, usuario.getId(), email,
                    AuthEvent.LOGIN_FAIL, AuthEvent.FAILURE, "Cuenta OAuth sin contraseña");
            throw new BadCredentialsException(
                    "Esta cuenta usa inicio de sesión con " + usuario.getOauthProvider()
                    + ". Por favor inicia sesión con ese proveedor.");
        }
        if (!passwordEncoder.matches(req.password(), usuario.getPasswordHash())) {
            authEvent.registrar(AuthEvent.ACTOR_DUENO, usuario.getId(), email,
                    AuthEvent.LOGIN_FAIL, AuthEvent.FAILURE, "Contraseña incorrecta");
            throw new BadCredentialsException("Credenciales inválidas");
        }
        Tenant tenant = tenantRepository.findById(usuario.getTenantId()).orElseThrow();
        verificarTenantOperativo(tenant);
        authEvent.registrar(AuthEvent.ACTOR_DUENO, usuario.getId(), email,
                AuthEvent.LOGIN, AuthEvent.SUCCESS, null);
        return emitirTokens(usuario, tenant);
    }

    /**
     * V27: solicita un magic link de login sin contraseña. SIEMPRE silencioso (204) — el
     * endpoint es público y revelar si el email existe permitiría enumerar cuentas. Solo
     * crea el token y manda el correo si el usuario existe Y su tenant está ACTIVO; si no,
     * no hace nada (anti-enumeración, igual que {@link PasswordResetService#solicitarResetPassword}).
     */
    @Transactional
    public void solicitarMagicLink(String email) {
        String emailNorm = AuthCrypto.normalizarEmail(email);
        if (emailNorm == null) return;
        usuarioRepository.findByEmail(emailNorm).ifPresent(usuario -> {
            Tenant tenant = tenantRepository.findById(usuario.getTenantId()).orElseThrow();
            if (!tenant.isActivo()) return;
            passwordResetTokenRepository.invalidarVigentesDeUsuarioAndTipo(
                    usuario.getId(), PasswordResetToken.TIPO_MAGIC);

            byte[] bytes = new byte[48];
            secureRandom.nextBytes(bytes);
            String tokenPlano = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

            PasswordResetToken token = new PasswordResetToken();
            token.setId(UUID.randomUUID());
            token.setUsuarioId(usuario.getId());
            token.setTokenHash(AuthCrypto.sha256(tokenPlano));
            token.setExpiraEn(OffsetDateTime.now().plusMinutes(magicLinkMinutos));
            token.setTipo(PasswordResetToken.TIPO_MAGIC);
            passwordResetTokenRepository.save(token);

            notificacion.magicLink(usuario, tokenPlano); // AFTER_COMMIT en el adaptador
        });
        authEvent.registrar(AuthEvent.ACTOR_DUENO, null, emailNorm,
                AuthEvent.MAGIC_LINK_REQUEST, AuthEvent.SUCCESS, null);
    }

    /**
     * V27: canjea un magic link. Mismo flujo que el login pero sin contraseña: valida el token
     * (de tipo MAGIC, vigente, no usado), lo marca usado y emite tokens. Emite el audit con
     * el actor que tiene el token (post-validación), así distinguimos magic-link correcto
     * de fallido sin enumerar emails en la respuesta.
     */
    @Transactional
    public TokenResponse entrarConMagicLink(String tokenPlano) {
        PasswordResetToken token = passwordResetTokenRepository
                .findByTokenHashAndTipo(AuthCrypto.sha256(tokenPlano), PasswordResetToken.TIPO_MAGIC)
                .filter(t -> t.vigente(OffsetDateTime.now()))
                .orElseThrow(() -> {
                    authEvent.registrar(AuthEvent.ACTOR_DUENO, null, "id:?",
                            AuthEvent.MAGIC_LOGIN, AuthEvent.FAILURE, "Token inválido o vencido");
                    return new BadCredentialsException("El enlace es inválido o ya venció; pide uno nuevo");
                });
        token.setUsado(true);

        Usuario usuario = usuarioRepository.findById(token.getUsuarioId()).orElseThrow();
        Tenant tenant = tenantRepository.findById(usuario.getTenantId()).orElseThrow();
        verificarTenantOperativo(tenant);
        authEvent.registrar(AuthEvent.ACTOR_DUENO, usuario.getId(), usuario.getEmail(),
                AuthEvent.MAGIC_LOGIN, AuthEvent.SUCCESS, "Login con magic link");
        return emitirTokens(usuario, tenant);
    }

    /**
     * RNF-02: refresh con rotación — el token usado se revoca y se emite uno nuevo.
     * Fix #2 (revisión de código): usar un token YA ROTADO es evidencia de robo (alguien
     * más tiene una copia) → se revoca la familia completa del usuario, no solo ese token.
     * noRollbackFor: la revocación masiva debe COMMITEARSE aunque respondamos 401.
     */
    @Transactional(noRollbackFor = BadCredentialsException.class)
    public TokenResponse refresh(RefreshRequest req) {
        RefreshToken actual = refreshTokenRepository.findByTokenHash(AuthCrypto.sha256(req.refreshToken()))
                .orElseThrow(() -> new BadCredentialsException("Refresh token inválido o expirado"));
        if (actual.isRevocado()) {
            refreshTokenRepository.revocarTodosDeUsuario(actual.getUsuarioId());
            authEvent.registrar(AuthEvent.ACTOR_DUENO, actual.getUsuarioId(), "id:" + actual.getUsuarioId(),
                    AuthEvent.THEFT_DETECTED, AuthEvent.FAILURE,
                    "Token ya rotado reusado — posible robo, sesiones revocadas");
            throw new BadCredentialsException("Refresh token inválido o expirado");
        }
        if (!actual.getExpiraEn().isAfter(OffsetDateTime.now())) {
            throw new BadCredentialsException("Refresh token inválido o expirado");
        }
        actual.setRevocado(true);

        Usuario usuario = usuarioRepository.findById(actual.getUsuarioId()).orElseThrow();
        Tenant tenant = tenantRepository.findById(usuario.getTenantId()).orElseThrow();
        // Offboarding (falla 3.3): el noRollbackFor de arriba hace que la rotación ya
        // ejecutada se COMMITEE — el refresh de un tenant suspendido/cerrado se quema al usarse.
        verificarTenantOperativo(tenant);
        authEvent.registrar(AuthEvent.ACTOR_DUENO, usuario.getId(), usuario.getEmail(),
                AuthEvent.REFRESH, AuthEvent.SUCCESS, null);
        return emitirTokens(usuario, tenant);
    }

    /**
     * Offboarding (falla 3.3, revisión a 5 años): un tenant SUSPENDIDO (morosidad, falla #10)
     * o CERRADO no puede iniciar sesión ni refrescar. Su página pública ya no existe
     * (el finder público filtra por estado ACTIVO).
     */
    private static void verificarTenantOperativo(Tenant tenant) {
        if (Tenant.ESTADO_SUSPENDIDO.equals(tenant.getEstado())) {
            throw new BadCredentialsException("El negocio está suspendido; contacta a soporte");
        }
        if (Tenant.ESTADO_CERRADO.equals(tenant.getEstado())) {
            throw new BadCredentialsException("Este negocio fue cerrado y sus accesos deshabilitados");
        }
    }

    /**
     * Fix #6 (revisión de código): /api/auth/** es permitAll, así que un logout con access
     * token vencido llegaba sin principal y respondía 204 SIN revocar nada (sesión fantasma).
     * Ahora también revoca por el hash del refresh token recibido en el body.
     */
    @Transactional
    public void logout(Long usuarioId, String refreshToken) {
        if (usuarioId != null) {
            refreshTokenRepository.revocarTodosDeUsuario(usuarioId);
            authEvent.registrar(AuthEvent.ACTOR_DUENO, usuarioId, "id:" + usuarioId,
                    AuthEvent.LOGOUT, AuthEvent.SUCCESS, null);
        }
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenRepository.findByTokenHash(AuthCrypto.sha256(refreshToken))
                    .ifPresent(t -> refreshTokenRepository.revocarTodosDeUsuario(t.getUsuarioId()));
        }
    }

    @Transactional
    public TokenResponse oauth2Login(String provider, String code, String redirectUri,
                                     String nombreNegocio, String slug) {
        OAuth2UserInfo info = oauth2Service.verify(provider, code, redirectUri);
        String email = AuthCrypto.normalizarEmail(info.email());

        var existingByProvider = usuarioRepository.findByOauthProviderAndOauthProviderId(
                info.provider(), info.providerId());
        if (existingByProvider.isPresent()) {
            Usuario usuario = existingByProvider.get();
            Tenant tenant = tenantRepository.findById(usuario.getTenantId()).orElseThrow();
            verificarTenantOperativo(tenant);
            authEvent.registrar(AuthEvent.ACTOR_DUENO, usuario.getId(), email,
                    AuthEvent.LOGIN, AuthEvent.SUCCESS, "OAuth2 " + provider);
            return emitirTokens(usuario, tenant);
        }

        var existingByEmail = usuarioRepository.findByEmail(email);
        if (existingByEmail.isPresent()) {
            Usuario usuario = existingByEmail.get();
            if (usuario.getPasswordHash() != null) {
                // La cuenta ya existe con contraseña; vincular OAuth
                usuario.setOauthProvider(provider);
                usuario.setOauthProviderId(info.providerId());
            }
            Tenant tenant = tenantRepository.findById(usuario.getTenantId()).orElseThrow();
            verificarTenantOperativo(tenant);
            authEvent.registrar(AuthEvent.ACTOR_DUENO, usuario.getId(), email,
                    AuthEvent.LOGIN, AuthEvent.SUCCESS, "OAuth2 " + provider + " vinculado");
            return emitirTokens(usuario, tenant);
        }

        // Nuevo registro por OAuth2: requiere nombreNegocio y slug
        if (nombreNegocio == null || nombreNegocio.isBlank()
                || slug == null || slug.isBlank()) {
            String pendingToken = tokenPort.emitirPendingRegistrationToken(email, provider, info.providerId());
            throw new OAuth2PendingRegistrationException(pendingToken);
        }
        if (SLUGS_RESERVADOS.contains(slug)) {
            throw new IllegalArgumentException("Ese slug está reservado, elige otro");
        }
        if (tenantRepository.existsBySlug(slug)) {
            throw new IllegalArgumentException("El slug ya está en uso");
        }

        Tenant tenant = new Tenant();
        tenant.setNombre(nombreNegocio);
        tenant.setSlug(slug);
        tenant = tenantRepository.save(tenant);

        horarioAtencionService.crearHorarioPorDefecto(tenant.getId());

        // RBAC: roles predefinidos para el personal
        rbacService.crearRolesPorDefecto(tenant.getId());

        Usuario usuario = new Usuario();
        usuario.setTenantId(tenant.getId());
        usuario.setEmail(email);
        usuario.setOauthProvider(provider);
        usuario.setOauthProviderId(info.providerId());
        usuarioRepository.save(usuario);

        authEvent.registrar(AuthEvent.ACTOR_DUENO, usuario.getId(), email,
                AuthEvent.LOGIN, AuthEvent.SUCCESS,
                "Registro OAuth2 " + provider + ": " + slug);
        return emitirTokens(usuario, tenant);
    }

    @Transactional
    public TokenResponse completarRegistroOAuth2(String pendingToken, String nombreNegocio, String slug) {
        Claims claims = tokenPort.validarPendingRegistrationToken(pendingToken);
        String email = claims.get("email", String.class);
        String provider = claims.get("provider", String.class);
        String providerId = claims.get("providerId", String.class);

        if (SLUGS_RESERVADOS.contains(slug)) {
            throw new IllegalArgumentException("Ese slug está reservado, elige otro");
        }
        if (tenantRepository.existsBySlug(slug)) {
            throw new IllegalArgumentException("El slug ya está en uso");
        }
        if (usuarioRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("El email ya está registrado");
        }

        Tenant tenant = new Tenant();
        tenant.setNombre(nombreNegocio);
        tenant.setSlug(slug);
        tenant = tenantRepository.save(tenant);

        horarioAtencionService.crearHorarioPorDefecto(tenant.getId());
        rbacService.crearRolesPorDefecto(tenant.getId());

        Usuario usuario = new Usuario();
        usuario.setTenantId(tenant.getId());
        usuario.setEmail(email);
        usuario.setOauthProvider(provider);
        usuario.setOauthProviderId(providerId);
        usuarioRepository.save(usuario);

        authEvent.registrar(AuthEvent.ACTOR_DUENO, usuario.getId(), email,
                AuthEvent.LOGIN, AuthEvent.SUCCESS,
                "Registro OAuth2 Completado " + provider + ": " + slug);
        return emitirTokens(usuario, tenant);
    }

    private TokenResponse emitirTokens(Usuario usuario, Tenant tenant) {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        String refreshPlano = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        RefreshToken refresh = new RefreshToken();
        refresh.setId(UUID.randomUUID());
        refresh.setUsuarioId(usuario.getId());
        refresh.setTokenHash(AuthCrypto.sha256(refreshPlano));
        refresh.setExpiraEn(OffsetDateTime.now().plusDays(refreshDays));
        refreshTokenRepository.save(refresh);

        return new TokenResponse(tokenPort.emitirAccessToken(usuario), refreshPlano,
                tenant.getSlug(), tenant.getNombre());
    }
}
