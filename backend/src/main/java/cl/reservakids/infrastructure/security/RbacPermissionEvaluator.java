package cl.reservakids.infrastructure.security;

import cl.reservakids.application.usecase.RbacService;
import cl.reservakids.infrastructure.web.StaffController;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.io.Serializable;

/**
 * Evaluador de permisos RBAC para {@code @PreAuthorize("hasPermission(...)")}.
 *
 * <p>Formato de uso en anotaciones:</p>
 * <pre>{@code @PreAuthorize("hasPermission(#tenantId, 'reservakids', 'reservas:leer')")}</pre>
 *
 * <p>La lógica:</p>
 * <ul>
 *   <li>Si el principal tiene rol {@code ROLE_DUENO} o {@code ROLE_ADMIN} → siempre {@code true}.</li>
 *   <li>Si tiene rol {@code ROLE_STAFF} → consulta {@link RbacService#tienePermiso} que
 *       resuelve los permisos desde el rol asignado del staff.</li>
 *   <li>Cualquier otro rol → {@code false}.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class RbacPermissionEvaluator implements PermissionEvaluator {

    private final RbacService rbacService;

    @Override
    public boolean hasPermission(Authentication auth, Object targetDomainObject, Object permission) {
        if (auth == null || !auth.isAuthenticated()) return false;

        AuthPrincipal principal = (AuthPrincipal) auth.getPrincipal();
        String permiso = permission.toString();

        return rbacService.tienePermiso(
                principal.usuarioId(), principal.tenantId(), principal.rol(), permiso);
    }

    @Override
    public boolean hasPermission(Authentication auth, Serializable targetId, String targetType, Object permission) {
        return hasPermission(auth, targetId, permission);
    }
}
