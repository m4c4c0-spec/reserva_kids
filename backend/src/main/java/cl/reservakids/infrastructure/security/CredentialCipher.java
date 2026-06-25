package cl.reservakids.infrastructure.security;

import cl.reservakids.application.usecase.CredentialCipherPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Cifrado AES-256-GCM de credenciales de terceros en reposo (token Mercado Pago, secreto webhook, API key Khipu).
 * Cada cifrado usa un IV aleatorio de 12 bytes prefijado al ciphertext; HMAC-SHA256 para derivar clave desde
 * JWT_SECRET cuando no hay CRED_ENC_KEY explícita.
 */
@Slf4j
@Component
public class CredentialCipher implements CredentialCipherPort {

    private static final String PREFIJO = "enc:v1:";
    private static final String TRANSFORMACION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public CredentialCipher(@Value("${app.security.cred-key:}") String credKey,
                            @Value("${app.jwt.secret}") String jwtSecret) {
        byte[] material;
        if (credKey != null && !credKey.isBlank()) {
            material = Base64.getDecoder().decode(credKey.trim());
            if (material.length != 32) {
                throw new IllegalStateException("CRED_ENC_KEY debe ser base64 de exactamente 32 bytes (AES-256)");
            }
        } else {
            log.warn("CRED_ENC_KEY no configurada: derivando la clave de cifrado de JWT_SECRET. "
                    + "Define una clave dedicada en producción (openssl rand -base64 32).");
            material = sha256(jwtSecret.getBytes(StandardCharsets.UTF_8));
        }
        this.key = new SecretKeySpec(material, "AES");
    }

    /** Devuelve {@code enc:v1:<base64(iv|ciphertext|tag)>}. null/blank pasan sin tocar. */
    public String encrypt(String plano) {
        if (plano == null || plano.isBlank()) {
            return plano;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMACION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plano.getBytes(StandardCharsets.UTF_8));
            byte[] combinado = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, combinado, 0, iv.length);
            System.arraycopy(ct, 0, combinado, iv.length, ct.length);
            return PREFIJO + Base64.getEncoder().encodeToString(combinado);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo cifrar la credencial", e);
        }
    }

    /** Inverso de {@link #encrypt}. Un valor sin prefijo se considera legado en texto plano. */
    public String decrypt(String almacenado) {
        if (almacenado == null || almacenado.isBlank() || !almacenado.startsWith(PREFIJO)) {
            return almacenado; // legado en texto plano (o vacío): compatibilidad hacia atrás
        }
        try {
            byte[] combinado = Base64.getDecoder().decode(almacenado.substring(PREFIJO.length()));
            byte[] iv = new byte[IV_BYTES];
            System.arraycopy(combinado, 0, iv, 0, IV_BYTES);
            Cipher cipher = Cipher.getInstance(TRANSFORMACION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] pt = cipher.doFinal(combinado, IV_BYTES, combinado.length - IV_BYTES);
            return new String(pt, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo descifrar la credencial almacenada", e);
        }
    }

    private static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
