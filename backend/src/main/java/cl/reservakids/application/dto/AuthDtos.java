package cl.reservakids.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {}

    public record RegistroRequest(
            @NotBlank @Size(max = 120) String nombreNegocio,
            @NotBlank @Pattern(regexp = "[a-z0-9-]{3,60}", message = "slug: solo minúsculas, números y guiones")
            String slug,
            @NotBlank @Email @Size(max = 160) String email,
            @NotBlank @Size(min = 8, max = 72) String password) {}

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {}

    public record RefreshRequest(@NotBlank String refreshToken) {}

    /** Falla 1.3 (revisión a 5 años): recuperación de contraseña. */
    public record ResetSolicitudRequest(@NotBlank @Email String email) {}

    public record ResetConfirmacionRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 8, max = 72) String nuevaPassword) {}

    /** V27: magic link de login sin contraseña. */
    public record MagicSolicitudRequest(@NotBlank @Email String email) {}

    public record MagicEntradaRequest(@NotBlank String token) {}

    public record TokenResponse(String accessToken, String refreshToken, String slug, String nombreNegocio) {}
}
