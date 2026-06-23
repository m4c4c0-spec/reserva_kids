package cl.reservakids.application.dto;

import java.util.List;

public final class MetricasDtos {

    private MetricasDtos() {}

    public record IngresoMensual(String mes, int totalClp) {}

    public record ServicioTop(String nombre, int cantidad) {}

    public record DashboardResponse(
            List<IngresoMensual> ingresosMensuales,
            List<ServicioTop> serviciosTop,
            int tasaOcupacionPorcentaje,
            int recaudadoTotalClp) {}
}
