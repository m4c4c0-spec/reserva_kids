package cl.reservakids.application.usecase;

/**
 * Puerto de trazabilidad de autenticación (RNF-07).
 * Registra eventos de login, refresh, logout, reset y detección de robo de token.
 */
public interface AuthEventPort {

    void registrar(String actorType, Long actorId, String email, String accion,
                   String resultado, String detalle);
}
