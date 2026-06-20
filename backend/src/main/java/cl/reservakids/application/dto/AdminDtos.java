package cl.reservakids.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Administrador de plataforma: login y respuesta de sesión. Sin auto-registro público. */
public final class AdminDtos {

    private AdminDtos() {}

    public record LoginAdminRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {}

    /** El refresh token viaja en cookie HttpOnly; el body solo expone el access. */
    public record AdminTokenResponse(String accessToken, String refreshToken, String email, String nombre) {}
}
