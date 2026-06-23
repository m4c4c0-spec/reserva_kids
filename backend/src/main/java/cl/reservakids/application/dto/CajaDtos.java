package cl.reservakids.application.dto;

import java.time.OffsetDateTime;

public final class CajaDtos {

    private CajaDtos() {}

    public record CajaDiariaResponse(
            String fecha,
            long totalReservas,
            long reservasConfirmadas,
            long citasAgendadas,
            long pagosHoy,
            long totalRecaudadoHoy,
            long saldoPendienteTotal,
            long seniaPromedio) {}

    public record ReservaCajaItem(
            Long id,
            String estado,
            String clienteNombre,
            String clienteTelefono,
            Integer totalClp,
            Integer seniaClp,
            Integer pagadoClp,
            Integer saldoClp,
            OffsetDateTime inicio,
            OffsetDateTime creadaEn) {}
}
