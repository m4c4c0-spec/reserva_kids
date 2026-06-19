package cl.reservakids.application.usecase;

import cl.reservakids.application.dto.ClienteDtos.*;
import cl.reservakids.domain.model.Cliente;
import cl.reservakids.domain.model.CuentaCliente;
import cl.reservakids.domain.model.RefreshTokenCliente;
import cl.reservakids.domain.repository.CuentaClienteRepository;
import cl.reservakids.domain.repository.RefreshTokenClienteRepository;
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
import java.util.Locale;
import java.util.UUID;

/**
 * Registro/login de cuentas de cliente (apoderados). Reusa BCrypt y el JWT del sistema,
 * pero emite tokens con rol CLIENTE y sin tenant. El email es identidad: se normaliza
 * (trim + minúsculas), igual que en {@link AuthService}.
 */
@Service
@RequiredArgsConstructor
public class ClienteAuthService {

    private final CuentaClienteRepository cuentaClienteRepository;
    private final RefreshTokenClienteRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenPort tokenPort;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.jwt.refresh-days}")
    private long refreshDays;

    private static String normalizarEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Reusa la normalización E.164 chilena de {@link Cliente} y valida que sea un celular
     * plausible (56 + 9 dígitos). Sin esto, un número mal escrito haría fallar el WhatsApp
     * de confirmación/recordatorio en silencio cuando se conecte el proveedor real.
     */
    private static String normalizarTelefono(String raw) {
        String tel = Cliente.normalizarTelefono(raw);
        if (tel == null || !tel.matches("56\\d{9}")) {
            throw new IllegalArgumentException(
                    "Teléfono inválido: usa un celular chileno, ej. +56 9 1234 5678");
        }
        return tel;
    }

    @Transactional
    public ClienteTokenResponse registrar(RegistroClienteRequest req) {
        String email = normalizarEmail(req.email());
        if (cuentaClienteRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Ya existe una cuenta con ese email");
        }
        CuentaCliente cuenta = new CuentaCliente();
        cuenta.setEmail(email);
        cuenta.setNombre(req.nombre());
        cuenta.setTelefono(normalizarTelefono(req.telefono()));
        cuenta.setPasswordHash(passwordEncoder.encode(req.password()));
        cuenta = cuentaClienteRepository.save(cuenta);
        return emitirTokens(cuenta);
    }

    @Transactional
    public ClienteTokenResponse login(LoginClienteRequest req) {
        CuentaCliente cuenta = cuentaClienteRepository.findByEmail(normalizarEmail(req.email()))
                .orElseThrow(() -> new BadCredentialsException("Credenciales inválidas"));
        if (!passwordEncoder.matches(req.password(), cuenta.getPasswordHash())) {
            throw new BadCredentialsException("Credenciales inválidas");
        }
        return emitirTokens(cuenta);
    }

    /**
     * V16: refresh con rotación para cuentas de cliente. Mismo comportamiento seguro que
     * el dueño: el token usado se revoca, usar un token ya rotado revoca toda la familia.
     */
    @Transactional(noRollbackFor = BadCredentialsException.class)
    public ClienteTokenResponse refresh(String refreshTokenPlano) {
        RefreshTokenCliente actual = refreshTokenRepository.findByTokenHash(sha256(refreshTokenPlano))
                .orElseThrow(() -> new BadCredentialsException("Refresh token inválido o expirado"));
        if (actual.isRevocado()) {
            refreshTokenRepository.revocarTodosDeCuenta(actual.getCuentaClienteId());
            throw new BadCredentialsException("Refresh token inválido o expirado");
        }
        if (!actual.getExpiraEn().isAfter(OffsetDateTime.now())) {
            throw new BadCredentialsException("Refresh token inválido o expirado");
        }
        actual.setRevocado(true);

        CuentaCliente cuenta = cuentaClienteRepository.findById(actual.getCuentaClienteId())
                .orElseThrow(() -> new BadCredentialsException("Cuenta no encontrada"));
        return emitirTokens(cuenta);
    }

    @Transactional
    public void logout(Long cuentaId, String refreshTokenPlano) {
        if (cuentaId != null) {
            refreshTokenRepository.revocarTodosDeCuenta(cuentaId);
        }
        if (refreshTokenPlano != null && !refreshTokenPlano.isBlank()) {
            refreshTokenRepository.findByTokenHash(sha256(refreshTokenPlano))
                    .ifPresent(t -> refreshTokenRepository.revocarTodosDeCuenta(t.getCuentaClienteId()));
        }
    }

    private ClienteTokenResponse emitirTokens(CuentaCliente cuenta) {
        String access = tokenPort.emitirAccessTokenCliente(cuenta.getId(), cuenta.getEmail());
        String refreshPlano = generarTokenPlano();

        RefreshTokenCliente refresh = new RefreshTokenCliente();
        refresh.setId(UUID.randomUUID());
        refresh.setCuentaClienteId(cuenta.getId());
        refresh.setTokenHash(sha256(refreshPlano));
        refresh.setExpiraEn(OffsetDateTime.now().plusDays(refreshDays));
        refreshTokenRepository.save(refresh);

        return new ClienteTokenResponse(access, refreshPlano, cuenta.getEmail(), cuenta.getNombre(), cuenta.getTelefono());
    }

    private String generarTokenPlano() {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
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
