package cl.reservakids.application.usecase;

import cl.reservakids.domain.model.Usuario;

/** Puerto de emisión de tokens — implementado en infraestructura (JWT). */
public interface TokenPort {
    String emitirAccessToken(Usuario usuario);
}
