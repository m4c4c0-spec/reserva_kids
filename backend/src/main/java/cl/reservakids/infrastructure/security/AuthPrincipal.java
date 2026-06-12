package cl.reservakids.infrastructure.security;

/** Identidad autenticada extraída del JWT: usuario + tenant (aislamiento multi-tenant). */
public record AuthPrincipal(Long usuarioId, Long tenantId, String rol) {
}
