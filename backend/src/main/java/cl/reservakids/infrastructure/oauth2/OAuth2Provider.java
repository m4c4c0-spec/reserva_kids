package cl.reservakids.infrastructure.oauth2;

import org.springframework.security.authentication.BadCredentialsException;

public interface OAuth2Provider {
    String getProviderName();
    OAuth2UserInfo verify(String code, String redirectUri) throws BadCredentialsException;
}
