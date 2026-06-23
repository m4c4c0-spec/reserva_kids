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
public class MicrosoftOAuth2Provider implements OAuth2Provider {

    private static final String PROVIDER = "microsoft";
    private static final String TOKEN_URL = "https://login.microsoftonline.com/common/oauth2/v2.0/token";
    private static final String USERINFO_URL = "https://graph.microsoft.com/v1.0/me";

    private final String clientId;
    private final String clientSecret;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public MicrosoftOAuth2Provider(
            @Value("${app.oauth2.microsoft.client-id:}") String clientId,
            @Value("${app.oauth2.microsoft.client-secret:}") String clientSecret,
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
            throw new BadCredentialsException("Microsoft OAuth2 no está configurado");
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
                        throw new BadCredentialsException("Microsoft rechazó el código de autorización");
                    })
                    .body(String.class);
            JsonNode json = objectMapper.readTree(response);
            if (json.has("error")) {
                throw new BadCredentialsException("Microsoft: " + json.get("error").asText());
            }
            return json.get("access_token").asText();
        } catch (BadCredentialsException e) {
            throw e;
        } catch (Exception e) {
            throw new BadCredentialsException("Error al conectar con Microsoft: " + e.getMessage());
        }
    }

    private OAuth2UserInfo fetchUserInfo(String accessToken) {
        try {
            String response = restClient.get()
                    .uri(URI.create(USERINFO_URL))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .onStatus(s -> s.value() >= 400, (req, res) -> {
                        throw new BadCredentialsException("Microsoft rechazó el token de acceso");
                    })
                    .body(String.class);
            JsonNode json = objectMapper.readTree(response);
            String sub = json.get("id").asText();
            String email = json.has("mail") ? json.get("mail").asText()
                    : json.has("userPrincipalName") ? json.get("userPrincipalName").asText()
                    : null;
            if (email == null || email.isBlank()) {
                throw new BadCredentialsException("No se pudo obtener el email de Microsoft");
            }
            String name = json.has("displayName") ? json.get("displayName").asText() : null;
            return new OAuth2UserInfo(PROVIDER, sub, email, name);
        } catch (BadCredentialsException e) {
            throw e;
        } catch (Exception e) {
            throw new BadCredentialsException("Error al obtener perfil de Microsoft: " + e.getMessage());
        }
    }
}
