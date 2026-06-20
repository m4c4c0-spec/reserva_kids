package cl.reservakids.infrastructure.security;

import cl.reservakids.application.usecase.TokenPort;
import cl.reservakids.domain.model.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * RNF-02: JWT HS256 verificado con iss/aud/exp. Access token de 15 minutos.
 */
@Service
public class JwtService implements TokenPort {

    private final SecretKey key;
    private final String issuer;
    private final String audience;
    private final Duration accessTtl;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.issuer}") String issuer,
                      @Value("${app.jwt.audience}") String audience,
                      @Value("${app.jwt.access-minutes}") long accessMinutes) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("JWT_SECRET debe tener al menos 32 caracteres");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.audience = audience;
        this.accessTtl = Duration.ofMinutes(accessMinutes);
    }

    @Override
    public String emitirAccessToken(Usuario usuario) {
        Instant ahora = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(usuario.getId()))
                .issuer(issuer)
                .audience().add(audience).and()
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plus(accessTtl)))
                .claim("tenantId", usuario.getTenantId())
                .claim("rol", usuario.getRol())
                .signWith(key)
                .compact();
    }

    /** Cuenta de cliente: rol CLIENTE y SIN tenantId (no pertenece a ningún negocio). */
    @Override
    public String emitirAccessTokenCliente(Long cuentaId, String email) {
        Instant ahora = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(cuentaId))
                .issuer(issuer)
                .audience().add(audience).and()
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plus(accessTtl)))
                .claim("rol", "CLIENTE")
                .claim("email", email)
                .signWith(key)
                .compact();
    }

    /** Administrador de plataforma: rol ADMIN y SIN tenantId (gobierna todos los negocios). */
    @Override
    public String emitirAccessTokenAdmin(Long adminId, String email) {
        Instant ahora = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(adminId))
                .issuer(issuer)
                .audience().add(audience).and()
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plus(accessTtl)))
                .claim("rol", "ADMIN")
                .claim("email", email)
                .signWith(key)
                .compact();
    }

    /** Lanza JwtException si la firma, iss, aud o exp no son válidos. */
    public AuthPrincipal validar(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .requireAudience(audience)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        // Jackson deserializa números pequeños como Integer: pedir Long.class directo
        // lanzaría ClassCastException en cuanto un tenantId real viaje en el token.
        // Las cuentas de cliente (rol CLIENTE) NO llevan tenantId → null.
        Number tenantId = (Number) claims.get("tenantId");
        return new AuthPrincipal(
                Long.valueOf(claims.getSubject()),
                tenantId == null ? null : tenantId.longValue(),
                claims.get("rol", String.class));
    }
}
