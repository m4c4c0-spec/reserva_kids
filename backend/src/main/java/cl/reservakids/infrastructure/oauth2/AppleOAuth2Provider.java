package cl.reservakids.infrastructure.oauth2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

@Component
public class AppleOAuth2Provider implements OAuth2Provider {

    private static final String PROVIDER = "apple";
    private static final String TOKEN_URL = "https://appleid.apple.com/auth/token";

    private final String clientId;
    private final String teamId;
    private final String keyId;
    private final PrivateKey privateKey;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public AppleOAuth2Provider(
            @Value("${app.oauth2.apple.client-id:}") String clientId,
            @Value("${app.oauth2.apple.team-id:}") String teamId,
            @Value("${app.oauth2.apple.key-id:}") String keyId,
            @Value("${app.oauth2.apple.private-key:}") String privateKeyRaw,
            ObjectMapper objectMapper) {
        this.clientId = clientId;
        this.teamId = teamId;
        this.keyId = keyId;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().build();
        this.privateKey = parsePrivateKey(privateKeyRaw);
    }

    private static PrivateKey parsePrivateKey(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            String pem = raw.replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(pem);
            return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("APPLE_PRIVATE_KEY inválida: " + e.getMessage());
        }
    }

    @Override
    public String getProviderName() {
        return PROVIDER;
    }

    @Override
    public OAuth2UserInfo verify(String code, String redirectUri) {
        if (clientId == null || clientId.isBlank() || privateKey == null) {
            throw new BadCredentialsException("Apple OAuth2 no está configurado");
        }

        String clientSecret = generateClientSecret();
        return exchangeCode(code, clientSecret, redirectUri);
    }

    private String generateClientSecret() {
        Instant now = Instant.now();
        return Jwts.builder()
                .header().keyId(keyId).and()
                .issuer(teamId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(300)))
                .audience().add("https://appleid.apple.com").and()
                .subject(clientId)
                .signWith(privateKey)
                .compact();
    }

    private OAuth2UserInfo exchangeCode(String code, String clientSecret, String redirectUri) {
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
                        throw new BadCredentialsException("Apple rechazó el código de autorización");
                    })
                    .body(String.class);
            JsonNode json = objectMapper.readTree(response);

            if (json.has("error")) {
                throw new BadCredentialsException("Apple: " + json.get("error").asText());
            }

            // Parsear el id_token (JWT) sin verificar firma — confiamos en que viene directo de Apple
            String idToken = json.get("id_token").asText();
            String[] parts = idToken.split("\\.");
            if (parts.length < 2) {
                throw new BadCredentialsException("id_token de Apple inválido");
            }
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            JsonNode claims = objectMapper.readTree(payload);

            String sub = claims.get("sub").asText();
            String email = claims.has("email") ? claims.get("email").asText() : null;
            String name = null;

            // El nombre SOLO viene en la primera autorización, en el form_post del frontend.
            // Lo recibimos opcionalmente como query param y lo pasamos vacío aquí;
            // el frontend lo completa en un paso posterior si es necesario.

            return new OAuth2UserInfo(PROVIDER, sub, email, name);
        } catch (BadCredentialsException e) {
            throw e;
        } catch (Exception e) {
            throw new BadCredentialsException("Error al conectar con Apple: " + e.getMessage());
        }
    }
}
