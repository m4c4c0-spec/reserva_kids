package cl.reservakids.application.dto;

import cl.reservakids.domain.model.Reserva;
import cl.reservakids.domain.model.Rut;
import jakarta.validation.constraints.*;

import java.util.List;

public final class ReservaDtos {

    private ReservaDtos() {}

    /** RF-05: formulario público de solicitud (fecha=bloque, servicio, nº niños, comuna, comentarios). */
    public record SolicitudPublicaRequest(
            @NotNull Long servicioId,
            @NotNull Long bloqueId,
            @NotBlank @Size(max = 120) String nombreContacto,
            @NotBlank @Rut String rut,
            @NotBlank @Size(max = 30) String telefono,
            @Email @Size(max = 160) String email,
            @Min(1) @Max(500) Integer numNinos,
            @Size(max = 80) String comuna,
            @Size(max = 2000) String comentarios,
            // RF-05: IDs de servicios adicionales (extras) elegidos en el sitio público.
            // Opcional; el backend valida que pertenezcan al negocio y sean adicionales activos.
            @Size(max = 20) List<@NotNull Long> adicionalIds,
            // Ley 21.719: consentimiento expreso del titular para tratar sus datos
            @NotNull @AssertTrue(message = "debes aceptar el tratamiento de tus datos para enviar la solicitud")
            Boolean aceptaDatos) {}

    public record CotizarRequest(
            @NotNull @Min(0) Integer totalClp,
            @NotNull @Min(0) Integer seniaClp) {}

    public record CancelarRequest(@Size(max = 500) String motivo) {}

    public record PagoRequest(
            @NotNull @Min(1) Integer montoClp,
            @NotBlank @Size(max = 30) String medio,
            @Size(max = 300) String comprobanteUrl,
            // ABONO (default) o DEVOLUCION — el monto siempre es positivo, el tipo da el signo
            @Pattern(regexp = "ABONO|DEVOLUCION") String tipo,
            @Size(max = 120) String referenciaExterna) {}

    public record ReservaResponse(
            Long id, String estado, Long servicioId, Long bloqueId, Long clienteId,
            Integer numNinos, String comuna, String comentarios,
            Integer totalClp, Integer seniaClp, Integer pagadoClp, Integer saldoClp,
            String creadaEn, String linkWhatsApp, String mpInitPoint) {

        public static ReservaResponse de(Reserva r, int pagado, String linkWhatsApp) {
            Integer saldo = r.getTotalClp() == null ? null : r.getTotalClp() - pagado;
            return new ReservaResponse(r.getId(), r.getEstado().name(), r.getServicioId(),
                    r.getBloqueId(), r.getClienteId(), r.getNumNinos(), r.getComuna(),
                    r.getComentarios(), r.getTotalClp(), r.getSeniaClp(), pagado, saldo,
                    r.getCreadaEn() == null ? null : r.getCreadaEn().toString(), linkWhatsApp,
                    r.getMpInitPoint());
        }
    }
}
