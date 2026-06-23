package cl.reservakids.application.dto;

import cl.reservakids.domain.model.Rut;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cuenta de cliente (apoderado): registro/login y directorio de negocios. */
public final class ClienteDtos {

    private ClienteDtos() {}

    public record RegistroClienteRequest(
            @NotBlank @Size(max = 120) String nombre,
            @NotBlank @Rut String rut,
            @NotBlank @Email @Size(max = 160) String email,
            @NotBlank @Size(max = 20) String telefono,
            @NotBlank @Size(min = 8, max = 72) String password) {}

    public record LoginClienteRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {}

    public record ClienteTokenResponse(String accessToken, String refreshToken, String email, String nombre, String telefono) {}

    /** Tarjeta del directorio: lo mínimo para listar y enlazar a /{slug}. */
    public record NegocioResumen(String slug, String nombre) {}
}
