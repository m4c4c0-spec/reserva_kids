package cl.reservakids.application.usecase;

import cl.reservakids.infrastructure.oauth2.OAuth2UserInfo;

/**
 * Puerto de aplicación para autenticación OAuth2/SSO.
 * La capa de infraestructura implementa este puerto con proveedores concretos
 * (Google, Microsoft, Apple), cumpliendo DIP en arquitectura hexagonal.
 */
public interface OAuth2Port {

    OAuth2UserInfo verify(String provider, String code, String redirectUri);
}
