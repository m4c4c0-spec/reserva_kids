package cl.reservakids.application.dto;

import cl.reservakids.domain.model.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

/** Offboarding de tenant (falla 3.3, revisión a 5 años). */
public final class TenantDtos {

    private TenantDtos() {}

    /** Anti cierre accidental: el dueño debe escribir el slug exacto de su negocio. */
    public record CerrarRequest(@NotBlank String slugConfirmacion) {}

    /** S3: mpAccessToken requerido; mpWebhookSecret opcional (el secreto de firma del webhook). */
    public record ActualizarTokenRequest(
            @NotBlank(message = "El Access Token de Mercado Pago es obligatorio")
            @Size(max = 500) String mpAccessToken,
            @Size(max = 500) String mpWebhookSecret) {}

    /**
     * V25: configuración de la pasarela de pago en línea (multi-pasarela). El dueño elige su
     * pasarela y carga solo las credenciales de la elegida; las que lleguen vacías se dejan
     * como están (no se borran al cambiar de pestaña). La validación de "creds obligatorias
     * según la pasarela elegida" se hace en {@code TenantService.guardarConfiguracion}.
     */
    public record ActualizarConfigRequest(
            @NotBlank(message = "Debes elegir una pasarela de pago")
            @jakarta.validation.constraints.Pattern(regexp = "MERCADOPAGO|KHIPU",
                    message = "Pasarela no soportada")
            String pasarelaPago,
            @Size(max = 500) String mpAccessToken,
            @Size(max = 500) String mpWebhookSecret,
            @Size(max = 512) String khipuApiKey,
            Long khipuReceiverId) {}

    /**
     * V26: teléfono de WhatsApp del salón, para el botón flotante de ayuda del mini-sitio
     * público. El dueño lo escribe como quiera (con o sin +56, espacios, etc.) y el servicio
     * lo normaliza a E.164 sin '+' (formato que wa.me espera). Vacío = quitarlo (oculta el botón).
     */
    public record ActualizarContactoRequest(String telefonoContacto) {}

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
