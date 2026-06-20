package cl.reservakids.application.usecase;

import cl.reservakids.application.dto.AuthDtos.ResetConfirmacionRequest;
import cl.reservakids.domain.model.PasswordResetToken;
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

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;

/**
 * Falla 1.3 (revisión a 5 años): recuperación de contraseña del dueño. Extraído de
 * {@link AuthService} para mantener cohesivo el flujo de reset (solicitud + confirmación)
 * y bajar el acoplamiento del servicio de autenticación.
 */
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final UsuarioRepository usuarioRepository;
    private final TenantRepository tenantRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificacionPort notificacion;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.password-reset.minutos}")
    private long resetMinutos;

    /**
     * Falla 1.3: solicitud de recuperación de contraseña.
     * SIEMPRE silenciosa (204) — el endpoint es público: revelar si el email existe
     * permitiría enumerar cuentas. El rate limit de /api/auth/** acota el abuso.
     * Pedir un reset nuevo invalida los enlaces anteriores (solo el último sirve).
     */
    @Transactional
    public void solicitarResetPassword(String email) {
        usuarioRepository.findByEmail(AuthCrypto.normalizarEmail(email)).ifPresent(usuario -> {
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
            token.setTokenHash(AuthCrypto.sha256(tokenPlano));
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
        PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(AuthCrypto.sha256(req.token()))
                .filter(t -> t.vigente(OffsetDateTime.now()))
                .orElseThrow(() -> new BadCredentialsException(
                        "El enlace es inválido o ya venció; pide uno nuevo"));
        token.setUsado(true);

        Usuario usuario = usuarioRepository.findById(token.getUsuarioId()).orElseThrow();
        usuario.setPasswordHash(passwordEncoder.encode(req.nuevaPassword()));
        refreshTokenRepository.revocarTodosDeUsuario(usuario.getId());
    }
}
