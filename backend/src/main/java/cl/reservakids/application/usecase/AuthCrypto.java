package cl.reservakids.application.usecase;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Helpers de autenticación compartidos por {@link AuthService} y {@link PasswordResetService}:
 * hash de tokens (SHA-256) y normalización del email de login. Sin estado, solo estáticos.
 */
final class AuthCrypto {

    private AuthCrypto() {
    }

    /**
     * El email es identidad de login: se guarda y se busca SIEMPRE normalizado
     * (trim + minúsculas). Sin esto, registrarse como "Dueno@Test.cl" hacía
     * imposible entrar (o recibir el reset) escribiendo "dueno@test.cl".
     */
    static String normalizarEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    static String sha256(String valor) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
