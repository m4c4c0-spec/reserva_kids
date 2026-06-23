package cl.reservakids.infrastructure.oauth2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.net.URI;

@Component
public class GoogleOAuth2Provider implements OAuth2Provider {

    private static final String PROVIDER = "google";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String USERINFO_URL = "https://www.googleapis.com/oauth2/v3/userinfo";

    private final String clientId;
    private final String clientSecret;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public GoogleOAuth2Provider(
            @Value("${app.oauth2.google.client-id:}") String clientId,
            @Value("${app.oauth2.google.client-secret:}") String clientSecret,
            ObjectMapper objectMapper) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().build();
    }

    @Override
    public String getProviderName() {
        return PROVIDER;
    }

    @Override
    public OAuth2UserInfo verify(String code, String redirectUri) {
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            throw new BadCredentialsException("Google OAuth2 no está configurado");
        }

        String accessToken = exchangeCode(code, redirectUri);
        return fetchUserInfo(accessToken);
    }

    private String exchangeCode(String code, String redirectUri) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("code", code);
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("redirect_uri", redirectUri);
        body.add("grant_type", "authorization_code");

        try {
            String response = restClient.post()
                    .uri(URI.create(TOKEN_URL))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .onStatus(s -> s.value() >= 400, (req, res) -> {
                        throw new BadCredentialsException("Google rechazó el código de autorización");
                    })
                    .body(String.class);
            JsonNode json = objectMapper.readTree(response);
            if (json.has("error")) {
                throw new BadCredentialsException("Google: " + json.get("error").asText());
            }
            return json.get("access_token").asText();
        } catch (BadCredentialsException e) {
            throw e;
        } catch (Exception e) {
            throw new BadCredentialsException("Error al conectar con Google: " + e.getMessage());
        }
    }

    private OAuth2UserInfo fetchUserInfo(String accessToken) {
        try {
            String response = restClient.get()
                    .uri(URI.create(USERINFO_URL))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .onStatus(s -> s.value() >= 400, (req, res) -> {
                        throw new BadCredentialsException("Google rechazó el token de acceso");
                    })
                    .body(String.class);
            JsonNode json = objectMapper.readTree(response);
            String sub = json.get("sub").asText();
            String email = json.get("email").asText();
            boolean emailVerified = json.has("email_verified") && json.get("email_verified").asBoolean();
            if (!emailVerified) {
                throw new BadCredentialsException("El email de Google no está verificado");
            }
            String name = json.has("name") ? json.get("name").asText() : null;
            return new OAuth2UserInfo(PROVIDER, sub, email, name);
        } catch (BadCredentialsException e) {
            throw e;
        } catch (Exception e) {
            throw new BadCredentialsException("Error al obtener perfil de Google: " + e.getMessage());
        }
    }
}
