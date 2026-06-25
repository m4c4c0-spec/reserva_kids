package cl.reservakids.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class StaffDtos {

    private StaffDtos() {}

    public record StaffRequest(
            @NotBlank @Size(max = 120) String nombre,
            @NotBlank @Email @Size(max = 160) String email,
            @Size(max = 20) String telefono,
            @Size(max = 30) String rol) {}

    /** Alta de un miembro del staff por el dueño: datos + contraseña inicial + rol RBAC. */
    public record StaffCrearRequest(
            @NotBlank @Size(max = 120) String nombre,
            @NotBlank @Email @Size(max = 160) String email,
            @Size(max = 20) String telefono,
            @Size(max = 30) String rol,
            Long rolPersonalId,
            @NotBlank @Size(min = 8, max = 72) String password) {}

    public record StaffLoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {}

    public record StaffTokenResponse(
            String accessToken, String refreshToken,
            Long staffId, String nombre, String rol, Long tenantId) {}

    public record StaffResponse(
            Long id,
            String nombre,
            String email,
            String telefono,
            String rol,
            Long rolPersonalId,
            String rolNombre,
            boolean activo) {}

    /** Catálogo de permisos disponibles para armar roles. */
    public record PermisoResponse(Long id, String codigo, String nombre, String categoria) {}

    /** Rol de personal con sus permisos. */
    public record RolResponse(
            Long id, String nombre, String descripcion,
            java.util.List<PermisoResponse> permisos,
            String creadoEn) {}

    public record RolCrearRequest(
            @NotBlank @Size(max = 60) String nombre,
            @Size(max = 200) String descripcion,
            @Size(min = 1, message = "Selecciona al menos un permiso")
            java.util.List<String> permisos) {}

    public record RolActualizarRequest(
            @Size(max = 60) String nombre,
            @Size(max = 200) String descripcion,
            java.util.List<String> permisos) {}

    public record StaffActualizarRequest(
            @Size(max = 120) String nombre,
            Long rolPersonalId) {}

    /** Vista pública del staff: solo nombre y rol, sin datos de contacto ni internos. */
    public record StaffPublicResponse(String nombre, String rol) {
        public StaffPublicResponse {
            if (rol == null || rol.isBlank()) rol = "Staff";
        }
    }
}
