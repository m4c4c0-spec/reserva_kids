package cl.reservakids.application.usecase;

import cl.reservakids.application.dto.AuthDtos.*;
import cl.reservakids.domain.model.RefreshToken;
import cl.reservakids.domain.model.Tenant;
import cl.reservakids.domain.model.Usuario;
import cl.reservakids.domain.repository.RefreshTokenRepository;
import cl.reservakids.domain.repository.TenantRepository;
import cl.reservakids.domain.repository.UsuarioRepository;
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
    private final HorarioAtencionService horarioAtencionService;
    private final PasswordEncoder passwordEncoder;
    private final TokenPort tokenPort;
    private final SecureRandom secureRandom = new SecureRandom();

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

        Usuario usuario = new Usuario();
        usuario.setTenantId(tenant.getId());
        usuario.setEmail(email);
        usuario.setPasswordHash(passwordEncoder.encode(req.password()));
        usuarioRepository.save(usuario);

        return emitirTokens(usuario, tenant);
    }

    @Transactional
    public TokenResponse login(LoginRequest req) {
        Usuario usuario = usuarioRepository.findByEmail(AuthCrypto.normalizarEmail(req.email()))
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
        RefreshToken actual = refreshTokenRepository.findByTokenHash(AuthCrypto.sha256(req.refreshToken()))
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
            refreshTokenRepository.findByTokenHash(AuthCrypto.sha256(refreshToken))
                    .ifPresent(t -> refreshTokenRepository.revocarTodosDeUsuario(t.getUsuarioId()));
        }
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
