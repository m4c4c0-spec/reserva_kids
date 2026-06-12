package cl.reservakids.application.dto;

import cl.reservakids.domain.model.BloqueDisponible;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

public final class CalendarioDtos {

    private CalendarioDtos() {}

    public record BloqueRequest(
            @NotNull LocalDate fecha,
            @NotNull LocalTime horaInicio,
            @NotNull LocalTime horaFin) {}

    public record BloqueResponse(Long id, LocalDate fecha, LocalTime horaInicio, LocalTime horaFin, String estado) {

        public static BloqueResponse de(BloqueDisponible b) {
            return new BloqueResponse(b.getId(), b.getFecha(), b.getHoraInicio(), b.getHoraFin(),
                    b.getEstado().name());
        }
    }
}
