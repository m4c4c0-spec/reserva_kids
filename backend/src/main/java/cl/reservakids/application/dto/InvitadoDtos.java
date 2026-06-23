package cl.reservakids.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public final class InvitadoDtos {

    private InvitadoDtos() {}

    public record InvitadoRequest(
            @NotBlank @Size(max = 120) String nombre,
            @Size(max = 160) String email,
            @Size(max = 20) String telefono) {}

    public record InvitadoResponse(
            Long id,
            String nombre,
            String email,
            String telefono,
            String estado,
            String token,
            String comentarios,
            OffsetDateTime creadoEn) {}

    public record InvitadoRsrvRequest(
            @Size(max = 500) String comentarios) {}

    /** Resumen de RSVP para mostrar en el panel del dueño. */
    public record InvitadoResumen(
            long total,
            long confirmados,
            long rechazados,
            long pendientes) {}
}
