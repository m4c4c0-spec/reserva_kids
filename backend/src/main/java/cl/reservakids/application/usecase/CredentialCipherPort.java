package cl.reservakids.application.usecase;

/**
 * Puerto de aplicación para cifrado/descifrado de credenciales en reposo.
 * La capa de infraestructura implementa este puerto con AES-256-GCM.
 */
public interface CredentialCipherPort {

    String encrypt(String plaintext);

    String decrypt(String ciphertext);
}
