package cl.reservakids.application.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Agendamiento de citas por hora (cliente autenticado). */
public final class AgendaDtos {

    private AgendaDtos() {}

    public record AgendarCitaRequest(
            @NotEmpty(message = "Elige al menos un servicio") List<@NotNull Long> servicioIds,
            @NotNull LocalDate fecha,
            @NotNull LocalTime hora) {}

    /** Tras agendar: la cita queda PENDIENTE_PAGO y se redirige al cliente a {@code initPoint}. */
    public record AgendarCitaResponse(Long reservaId, String initPoint, Integer totalClp, String inicio) {}
}
