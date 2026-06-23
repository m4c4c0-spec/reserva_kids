package cl.reservakids.application.usecase;

import cl.reservakids.application.dto.CajaDtos.*;
import cl.reservakids.domain.model.Reserva;
import cl.reservakids.domain.repository.PagoRepository;
import cl.reservakids.domain.repository.ReservaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CajaService {

    private final ReservaRepository reservaRepository;
    private final PagoRepository pagoRepository;

    @Transactional(readOnly = true)
    public CajaDiariaResponse cajaDiaria(Long tenantId, String fechaStr) {
        LocalDate fecha = LocalDate.parse(fechaStr);
        OffsetDateTime inicio = fecha.atStartOfDay(ZoneId.of("America/Santiago")).toOffsetDateTime();
        OffsetDateTime fin = fecha.plusDays(1).atStartOfDay(ZoneId.of("America/Santiago")).toOffsetDateTime();

        List<Reserva> todas = reservaRepository.findByTenantIdOrderByCreadaEnDesc(tenantId);

        // Filtrar reservas del dia (creadas hoy o con inicio hoy)
        List<Reserva> delDia = todas.stream()
                .filter(r -> {
                    if (r.getInicio() != null && !r.getInicio().isBefore(inicio) && r.getInicio().isBefore(fin))
                        return true;
                    if (r.getCreadaEn() != null && !r.getCreadaEn().isBefore(inicio) && r.getCreadaEn().isBefore(fin))
                        return true;
                    return false;
                })
                .toList();

        // Sumar pagos del dia
        List<Object[]> rawPagos = pagoRepository.totalesPagadosPorReserva(
                delDia.stream().map(Reserva::getId).toList());
        Map<Long, Integer> pagosPorReserva = new HashMap<>();
        for (Object[] row : rawPagos) {
            pagosPorReserva.put(((Number) row[0]).longValue(), ((Number) row[1]).intValue());
        }

        long totalRecaudado = 0;
        long reservasConSaldo = 0;
        long saldoTotal = 0;
        long seniaSuma = 0;
        int seniaCount = 0;

        for (Reserva r : delDia) {
            Integer pagado = pagosPorReserva.getOrDefault(r.getId(), 0);
            totalRecaudado += pagado;
            int saldo = (r.getTotalClp() != null ? r.getTotalClp() : 0) - pagado;
            if (saldo > 0) {
                reservasConSaldo++;
                saldoTotal += saldo;
            }
            if (r.getSeniaClp() != null && r.getSeniaClp() > 0) {
                seniaSuma += r.getSeniaClp();
                seniaCount++;
            }
        }

        long confirmadas = delDia.stream().filter(r -> "CONFIRMADA".equals(r.getEstado().name())).count();
        long citas = delDia.stream().filter(r -> r.getInicio() != null).count();

        long pagosHoy = delDia.stream().filter(r -> pagosPorReserva.containsKey(r.getId())).count();

        return new CajaDiariaResponse(
                fechaStr,
                delDia.size(),
                confirmadas,
                citas,
                pagosHoy,
                totalRecaudado,
                saldoTotal,
                seniaCount > 0 ? seniaSuma / seniaCount : 0
        );
    }
}
