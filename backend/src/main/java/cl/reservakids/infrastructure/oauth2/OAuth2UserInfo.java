package cl.reservakids.infrastructure.oauth2;

public record OAuth2UserInfo(String provider, String providerId, String email, String name) {}
