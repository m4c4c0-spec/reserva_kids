package cl.reservakids.application.usecase;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Helpers de autenticación compartidos entre servicios. Normalización de email (estático,
 * sin estado) y hashing de tokens/emails vía HMAC-SHA256 con clave derivada de JWT_SECRET.
 * Reemplaza el SHA-256 plano anterior: la clave secreta impide ataques de diccionario
 * contra el hash de email en el audit trail y añade defensa en profundidad a los tokens.
 */
@Component
public class AuthCrypto {

    private final byte[] hmacKey;

    public AuthCrypto(@Value("${app.jwt.secret:}") String jwtSecret) {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            jwtSecret = "reservakids-test-hmac-key-with-32-bytes-minimum!!";
        }
        this.hmacKey = sha256Bytes(jwtSecret);
    }

    public static String normalizarEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    /** HMAC-SHA256 de un token aleatorio (refresh, reset, magic link). */
    public String hashToken(String valor) {
        return hmacHex(valor);
    }

    /** HMAC-SHA256 de un email para el audit trail (PII hashed con clave secreta). */
    public String hashEmail(String email) {
        if (email == null) return "unknown";
        return hmacHex(email.trim().toLowerCase(Locale.ROOT));
    }

    private String hmacHex(String input) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacKey, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 no disponible", e);
        }
    }

    private static byte[] sha256Bytes(String valor) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(valor.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
