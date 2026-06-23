package cl.reservakids.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.List;

public final class SuscripcionDtos {

    private SuscripcionDtos() {}

    public record PlanResponse(String codigo, String nombre, int precioMensualClp, int precioAnualClp,
                                int maxServicios, int maxReservasMensuales, boolean tieneReportes) {
        public static List<PlanResponse> PLANES = List.of(
                new PlanResponse("GRATIS", "Plan Gratis", 0, 0, 5, 50, false),
                new PlanResponse("BASICO", "Plan Básico", 9900, 99000, 20, 200, true),
                new PlanResponse("PRO", "Plan Pro", 24900, 249000, 100, 1000, true));
    }

    public record SuscripcionRequest(
            @NotBlank String plan,
            @NotBlank String periodo) {}

    public record SuscripcionResponse(
            String plan, String estado, Integer montoClp, String periodo,
            OffsetDateTime inicio, OffsetDateTime renovacion, String referenciaExterna) {}

    public record SuscripcionPagoResponse(
            Long id, Integer montoClp, String estado, String periodo,
            OffsetDateTime fecha, String referenciaExterna) {}
}
