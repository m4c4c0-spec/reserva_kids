package cl.reservakids.domain.exception;

public class OAuth2PendingRegistrationException extends RuntimeException {
    
    private final String pendingToken;
    
    public OAuth2PendingRegistrationException(String pendingToken) {
        super("Faltan datos del negocio para completar el registro OAuth2");
        this.pendingToken = pendingToken;
    }
    
    public String getPendingToken() {
        return pendingToken;
    }
}
