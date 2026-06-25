package cl.reservakids.application.usecase;

import cl.reservakids.domain.model.Usuario;

/** Puerto de emisión de tokens — implementado en infraestructura (JWT). */
public interface TokenPort {
    String emitirAccessToken(Usuario usuario);

    /** Token para una cuenta de cliente (rol CLIENTE, sin tenant). */
    String emitirAccessTokenCliente(Long cuentaId, String email);

    /** Token para un administrador de plataforma (rol ADMIN, sin tenant). */
    String emitirAccessTokenAdmin(Long adminId, String email);

    /** Token temporal (15 min) para registro OAuth2 pendiente de datos del negocio. */
    String emitirPendingRegistrationToken(String email, String provider, String providerId);
    
    /** Valida el token temporal y devuelve las claims. */
    io.jsonwebtoken.Claims validarPendingRegistrationToken(String token);
}
