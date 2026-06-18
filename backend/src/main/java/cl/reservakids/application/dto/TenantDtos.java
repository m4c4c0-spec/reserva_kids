package cl.reservakids.application.dto;

import cl.reservakids.domain.model.*;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

/** Offboarding de tenant (falla 3.3, revisión a 5 años). */
public final class TenantDtos {

    private TenantDtos() {}

    /** Anti cierre accidental: el dueño debe escribir el slug exacto de su negocio. */
    public record CerrarRequest(@NotBlank String slugConfirmacion) {}

    /** S3: mpWebhookSecret es opcional (el secreto de firma del webhook del panel de MP). */
    public record ActualizarTokenRequest(String mpAccessToken, String mpWebhookSecret) {}

    /**
     * Export completo del tenant en JSON plano (agnóstico del motor de BD — falla 3.1):
     * es la copia del dueño al irse Y el insumo del cierre responsable del servicio (5.1).
     */
    public record ExportResponse(
            String nombre, String slug, String plan, String estado,
            OffsetDateTime creadoEn, OffsetDateTime cerradoEn, OffsetDateTime generadoEn,
            List<ServicioExport> servicios, List<ClienteExport> clientes,
            List<BloqueExport> bloques, List<ReservaExport> reservas, List<PagoExport> pagos) {}

    public record ServicioExport(Long id, String nombre, String descripcion, Integer precioClp,
                                 Integer duracionMin, Integer capacidad, boolean activo) {
        public static ServicioExport de(Servicio s) {
            return new ServicioExport(s.getId(), s.getNombre(), s.getDescripcion(),
                    s.getPrecioClp(), s.getDuracionMin(), s.getCapacidad(), s.isActivo());
        }
    }

    public record ClienteExport(Long id, String nombre, String telefono, String email,
                                OffsetDateTime consentimientoEn, boolean anonimizado) {
        public static ClienteExport de(Cliente c) {
            return new ClienteExport(c.getId(), c.getNombre(), c.getTelefono(), c.getEmail(),
                    c.getConsentimientoEn(), c.isAnonimizado());
        }
    }

    public record BloqueExport(Long id, LocalDate fecha, LocalTime horaInicio,
                               LocalTime horaFin, String estado) {
        public static BloqueExport de(BloqueDisponible b) {
            return new BloqueExport(b.getId(), b.getFecha(), b.getHoraInicio(),
                    b.getHoraFin(), b.getEstado().name());
        }
    }

    public record ReservaExport(Long id, Long clienteId, Long servicioId, Long bloqueId,
                                String estado, Integer numNinos, String comuna, String comentarios,
                                Integer totalClp, Integer seniaClp, OffsetDateTime creadaEn) {
        public static ReservaExport de(Reserva r) {
            return new ReservaExport(r.getId(), r.getClienteId(), r.getServicioId(), r.getBloqueId(),
                    r.getEstado().name(), r.getNumNinos(), r.getComuna(), r.getComentarios(),
                    r.getTotalClp(), r.getSeniaClp(), r.getCreadaEn());
        }
    }

    public record PagoExport(Long id, Long reservaId, Integer montoClp, String tipo, String estado,
                             String medio, String comprobanteUrl, OffsetDateTime fecha) {
        public static PagoExport de(Pago p) {
            return new PagoExport(p.getId(), p.getReservaId(), p.getMontoClp(), p.getTipo(),
                    p.getEstado(), p.getMedio(), p.getComprobanteUrl(), p.getFecha());
        }
    }
}
