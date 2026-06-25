package cl.reservakids.infrastructure.oauth2;

import cl.reservakids.application.usecase.OAuth2Port;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class OAuth2Service implements OAuth2Port {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final GoogleOAuth2Provider googleProvider;
    private final MicrosoftOAuth2Provider microsoftProvider;
    private final AppleOAuth2Provider appleProvider;

    private final String googleClientId;
    private final String microsoftClientId;
    private final String appleClientId;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    public OAuth2Service(
            GoogleOAuth2Provider googleProvider,
            MicrosoftOAuth2Provider microsoftProvider,
            AppleOAuth2Provider appleProvider,
            @Value("${app.oauth2.google.client-id:}") String googleClientId,
            @Value("${app.oauth2.microsoft.client-id:}") String microsoftClientId,
            @Value("${app.oauth2.apple.client-id:}") String appleClientId) {
        this.googleProvider = googleProvider;
        this.microsoftProvider = microsoftProvider;
        this.appleProvider = appleProvider;
        this.googleClientId = googleClientId;
        this.microsoftClientId = microsoftClientId;
        this.appleClientId = appleClientId;
    }

    public String buildAuthorizeUrl(String provider, String type) {
        String redirectUri = URLEncoder.encode(frontendUrl + "/oauth2/callback", StandardCharsets.UTF_8);
        String nonce = generarNonce();
        String state = URLEncoder.encode(type + "|" + provider + "|" + nonce, StandardCharsets.UTF_8);
        return switch (provider) {
            case "google" -> googleAuthorizeUrl(redirectUri, state, type);
            case "microsoft" -> microsoftAuthorizeUrl(redirectUri, state);
            case "apple" -> appleAuthorizeUrl(redirectUri, state);
            default -> throw new IllegalArgumentException("Proveedor OAuth2 no soportado: " + provider);
        };
    }

    /**
     * Verifica que el state devuelto por el proveedor sea válido.
     * Formato esperado: type|provider|nonce (al menos 2 pipes → 3 partes).
     */
    public void verifyState(String state, String expectedType, String expectedProvider) {
        if (state == null || state.isBlank()) {
            throw new BadCredentialsException("Falta el parámetro state (CSRF)");
        }
        String[] partes = state.split("\\|");
        if (partes.length < 3) {
            throw new BadCredentialsException("State inválido");
        }
        if (!partes[0].equals(expectedType) || !partes[1].equals(expectedProvider)) {
            throw new BadCredentialsException("State no coincide");
        }
    }

    public OAuth2UserInfo verify(String provider, String code, String redirectUri) {
        return getProvider(provider).verify(code, redirectUri);
    }

    private OAuth2Provider getProvider(String provider) {
        return switch (provider) {
            case "google" -> googleProvider;
            case "microsoft" -> microsoftProvider;
            case "apple" -> appleProvider;
            default -> throw new BadCredentialsException("Proveedor OAuth2 no soportado: " + provider);
        };
    }

    private String generarNonce() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String googleAuthorizeUrl(String redirectUri, String state, String type) {
        if (googleClientId == null || googleClientId.isBlank()) return null;
        
        String scopes = "openid%20email%20profile";
        String extraParams = "";
        
        if ("dueno".equals(type)) {
            scopes += "%20https://www.googleapis.com/auth/calendar.events";
            extraParams = "&access_type=offline&prompt=consent";
        }
        
        return "https://accounts.google.com/o/oauth2/v2/auth"
                + "?response_type=code"
                + "&client_id=" + googleClientId
                + "&redirect_uri=" + redirectUri
                + "&scope=" + scopes
                + extraParams
                + "&state=" + state;
    }

    private String microsoftAuthorizeUrl(String redirectUri, String state) {
        if (microsoftClientId == null || microsoftClientId.isBlank()) return null;
        return "https://login.microsoftonline.com/common/oauth2/v2.0/authorize"
                + "?response_type=code"
                + "&client_id=" + microsoftClientId
                + "&redirect_uri=" + redirectUri
                + "&scope=openid%20email%20profile"
                + "&response_mode=query"
                + "&state=" + state;
    }

    private String appleAuthorizeUrl(String redirectUri, String state) {
        if (appleClientId == null || appleClientId.isBlank()) return null;
        return "https://appleid.apple.com/auth/authorize"
                + "?response_type=code"
                + "&client_id=" + appleClientId
                + "&redirect_uri=" + redirectUri
                + "&scope=name%20email"
                + "&response_mode=query"
                + "&state=" + state;
    }
}
