package cl.reservakids.application.dto;

import cl.reservakids.domain.model.HorarioAtencion;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;
import java.util.List;

/** Configuración del horario de atención para el agendamiento por hora. */
public final class HorarioAtencionDtos {

    private HorarioAtencionDtos() {}

    public record FranjaRequest(
            @NotNull @Min(1) @Max(7) Integer diaSemana,
            @NotNull LocalTime horaApertura,
            @NotNull LocalTime horaCierre) {}

    public record HorarioRequest(
            @NotNull @Min(5) @Max(120) Integer intervaloMin,
            @NotNull List<@NotNull FranjaRequest> franjas) {}

    public record FranjaResponse(Integer diaSemana, LocalTime horaApertura, LocalTime horaCierre) {
        public static FranjaResponse de(HorarioAtencion h) {
            return new FranjaResponse((int) h.getDiaSemana(), h.getHoraApertura(), h.getHoraCierre());
        }
    }

    public record HorarioResponse(Integer intervaloMin, List<FranjaResponse> franjas) {}
}
