package cl.reservakids.application.usecase;

import cl.reservakids.application.dto.AuthDtos.*;
import cl.reservakids.domain.model.PasswordResetToken;
import cl.reservakids.domain.model.RefreshToken;
import cl.reservakids.domain.model.Tenant;
import cl.reservakids.domain.model.Usuario;
import cl.reservakids.domain.repository.PasswordResetTokenRepository;
import cl.reservakids.domain.repository.RefreshTokenRepository;
import cl.reservakids.domain.repository.TenantRepository;
import cl.reservakids.domain.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final TenantRepository tenantRepository;
    private final UsuarioRepository usuarioRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenPort tokenPort;
    private final NotificacionPort notificacion;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.jwt.refresh-days}")
    private long refreshDays;

    @Value("${app.password-reset.minutos}")
    private long resetMinutos;

    /**
     * Rutas del propio frontend/API: si un negocio se registrara con uno de estos slugs,
     * su página pública (/{slug}) quedaría inaccesible para siempre.
     */
    private static final java.util.Set<String> SLUGS_RESERVADOS = java.util.Set.of(
            "login", "panel", "api", "admin", "auth", "www", "app",
            "static", "assets", "public", "docs", "ayuda", "soporte",
            "reset", // falla 1.3: ruta del frontend para recuperar contraseña
            "clientes"); // área de cliente (login + directorio) — no puede ser un slug de negocio

    /**
     * El email es identidad de login: se guarda y se busca SIEMPRE normalizado
     * (trim + minúsculas). Sin esto, registrarse como "Dueno@Test.cl" hacía
     * imposible entrar (o recibir el reset) escribiendo "dueno@test.cl".
     */
    private static String normalizarEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    /** RF-01: registro de negocio (crea tenant + usuario dueño). */
    @Transactional
    public TokenResponse registrar(RegistroRequest req) {
        String email = normalizarEmail(req.email());
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

        Usuario usuario = new Usuario();
        usuario.setTenantId(tenant.getId());
        usuario.setEmail(email);
        usuario.setPasswordHash(passwordEncoder.encode(req.password()));
        usuarioRepository.save(usuario);

        return emitirTokens(usuario, tenant);
    }

    @Transactional
    public TokenResponse login(LoginRequest req) {
        Usuario usuario = usuarioRepository.findByEmail(normalizarEmail(req.email()))
                .orElseThrow(() -> new BadCredentialsException("Credenciales inválidas"));
        if (!passwordEncoder.matches(req.password(), usuario.getPasswordHash())) {
            throw new BadCredentialsException("Credenciales inválidas");
        }
        Tenant tenant = tenantRepository.findById(usuario.getTenantId()).orElseThrow();
        verificarTenantOperativo(tenant);
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
        RefreshToken actual = refreshTokenRepository.findByTokenHash(sha256(req.refreshToken()))
                .orElseThrow(() -> new BadCredentialsException("Refresh token inválido o expirado"));
        if (actual.isRevocado()) {
            refreshTokenRepository.revocarTodosDeUsuario(actual.getUsuarioId());
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
        }
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenRepository.findByTokenHash(sha256(refreshToken))
                    .ifPresent(t -> refreshTokenRepository.revocarTodosDeUsuario(t.getUsuarioId()));
        }
    }

    /**
     * Falla 1.3 (revisión a 5 años): solicitud de recuperación de contraseña.
     * SIEMPRE silenciosa (204) — el endpoint es público: revelar si el email existe
     * permitiría enumerar cuentas. El rate limit de /api/auth/** acota el abuso.
     * Pedir un reset nuevo invalida los enlaces anteriores (solo el último sirve).
     */
    @Transactional
    public void solicitarResetPassword(String email) {
        usuarioRepository.findByEmail(normalizarEmail(email)).ifPresent(usuario -> {
            Tenant tenant = tenantRepository.findById(usuario.getTenantId()).orElseThrow();
            if (!tenant.isActivo()) {
                return; // suspendido/cerrado (falla 3.3): sin reset, y sin revelar nada
            }
            passwordResetTokenRepository.invalidarVigentesDeUsuario(usuario.getId());

            byte[] bytes = new byte[48];
            secureRandom.nextBytes(bytes);
            String tokenPlano = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

            PasswordResetToken token = new PasswordResetToken();
            token.setId(UUID.randomUUID());
            token.setUsuarioId(usuario.getId());
            token.setTokenHash(sha256(tokenPlano));
            token.setExpiraEn(OffsetDateTime.now().plusMinutes(resetMinutos));
            passwordResetTokenRepository.save(token);

            notificacion.resetPassword(usuario, tokenPlano); // AFTER_COMMIT en el adaptador
        });
    }

    /**
     * Falla 1.3: confirma el reset — token de un solo uso y vencimiento corto.
     * Cambiar la contraseña revoca TODAS las sesiones (si alguien la robó, lo echa).
     */
    @Transactional
    public void confirmarResetPassword(ResetConfirmacionRequest req) {
        PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(sha256(req.token()))
                .filter(t -> t.vigente(OffsetDateTime.now()))
                .orElseThrow(() -> new BadCredentialsException(
                        "El enlace es inválido o ya venció; pide uno nuevo"));
        token.setUsado(true);

        Usuario usuario = usuarioRepository.findById(token.getUsuarioId()).orElseThrow();
        usuario.setPasswordHash(passwordEncoder.encode(req.nuevaPassword()));
        refreshTokenRepository.revocarTodosDeUsuario(usuario.getId());
    }

    private TokenResponse emitirTokens(Usuario usuario, Tenant tenant) {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        String refreshPlano = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        RefreshToken refresh = new RefreshToken();
        refresh.setId(UUID.randomUUID());
        refresh.setUsuarioId(usuario.getId());
        refresh.setTokenHash(sha256(refreshPlano));
        refresh.setExpiraEn(OffsetDateTime.now().plusDays(refreshDays));
        refreshTokenRepository.save(refresh);

        return new TokenResponse(tokenPort.emitirAccessToken(usuario), refreshPlano,
                tenant.getSlug(), tenant.getNombre());
    }

    private static String sha256(String valor) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
