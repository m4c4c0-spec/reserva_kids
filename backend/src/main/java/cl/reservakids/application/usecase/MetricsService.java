package cl.reservakids.application.usecase;

import cl.reservakids.application.dto.MetricasDtos.DashboardResponse;
import cl.reservakids.application.dto.MetricasDtos.IngresoMensual;
import cl.reservakids.application.dto.MetricasDtos.ServicioTop;
import cl.reservakids.domain.model.EstadoReserva;
import cl.reservakids.domain.repository.PagoRepository;
import cl.reservakids.domain.repository.ReservaRepository;
import cl.reservakids.domain.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MetricsService {

    private final ReservaRepository reservaRepository;
    private final TenantRepository tenantRepository;
    private final PagoRepository pagoRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public DashboardResponse dashboard(Long tenantId) {
        OffsetDateTime hace6Meses = OffsetDateTime.now(clock).minusMonths(6);
        YearMonth mesActual = YearMonth.now(clock);

        // Ingresos mensuales (últimos 6 meses)
        List<IngresoMensual> ingresos = reservaRepository.ingresosMensuales(tenantId, hace6Meses)
                .stream()
                .map(row -> new IngresoMensual((String) row[0], ((Number) row[1]).intValue()))
                .toList();

        // Servicios más vendidos (top 5)
        List<ServicioTop> topServicios = reservaRepository.serviciosMasVendidos(tenantId)
                .stream()
                .limit(5)
                .map(row -> new ServicioTop((String) row[0], ((Number) row[1]).intValue()))
                .toList();

        // Tasa de ocupación del mes
        long diasConReservas = reservaRepository.diasConReservas(tenantId,
                mesActual.atDay(1).atStartOfDay(clock.getZone()).toOffsetDateTime());
        int diasDelMes = mesActual.lengthOfMonth();
        int tasaOcupacion = diasDelMes > 0 ? (int) ((diasConReservas * 100L) / diasDelMes) : 0;

        // Total recaudado en señas
        Long recaudadoLong = pagoRepository.totalRecaudadoPlataforma();
        int recaudadoSenas = recaudadoLong != null ? recaudadoLong.intValue() : 0;

        return new DashboardResponse(ingresos, topServicios, tasaOcupacion, recaudadoSenas);
    }
}
