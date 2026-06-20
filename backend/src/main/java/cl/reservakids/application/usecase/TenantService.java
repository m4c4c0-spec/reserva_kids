package cl.reservakids.application.usecase;

import cl.reservakids.application.dto.TenantDtos.*;
import cl.reservakids.domain.exception.RecursoNoEncontradoException;
import cl.reservakids.domain.model.*;
import cl.reservakids.domain.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Offboarding de tenant (falla 3.3, revisión a 5 años): cierre a demanda y actualización
 * de credenciales. El export completo vive en {@link TenantExportService}; la purga física
 * diferida en {@link TenantPurgaJobs#purgarTenantsCerrados()}.
 * <p>
 * Cierre: PENDIENTE/COTIZADA se cancelan (nadie las atenderá); las CONFIRMADA (hay seña de
 * por medio: devolver dinero es decisión humana) exigen resolución manual previa. La página
 * pública desaparece al instante (el finder público filtra por estado ACTIVO) y las sesiones
 * se revocan.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;
    private final BloqueDisponibleRepository bloqueRepository;
    private final ReservaRepository reservaRepository;
    private final UsuarioRepository usuarioRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final cl.reservakids.infrastructure.security.CredentialCipher credentialCipher;
    private final TenantExportService tenantExportService;
    private final Clock clock;

    /**
     * Actualiza las credenciales de Mercado Pago del tenant.
     * S1/S3: se guardan cifradas en reposo (CredentialCipher). El secreto del webhook es
     * opcional: si llega vacío/null se deja como está (no se borra al guardar solo el token).
     */
    @Transactional
    public void actualizarTokenMp(Long tenantId, ActualizarTokenRequest req) {
        Tenant tenant = buscar(tenantId);
        tenant.setMpAccessToken(credentialCipher.encrypt(req.mpAccessToken()));
        if (req.mpWebhookSecret() != null && !req.mpWebhookSecret().isBlank()) {
            tenant.setMpWebhookSecret(credentialCipher.encrypt(req.mpWebhookSecret()));
        }
    }

    /**
     * Cierre a demanda del dueño. Devuelve el export final (última copia garantizada:
     * tras la ventana de gracia la purga es irreversible).
     */
    @Transactional
    public ExportResponse cerrar(Long tenantId, CerrarRequest req) {
        Tenant tenant = buscar(tenantId);
        if (Tenant.ESTADO_CERRADO.equals(tenant.getEstado())) {
            throw new IllegalArgumentException("El negocio ya está cerrado");
        }
        if (!tenant.getSlug().equals(req.slugConfirmacion())) {
            throw new IllegalArgumentException(
                    "La confirmación no coincide: escribe el slug exacto de tu negocio para cerrarlo");
        }
        if (reservaRepository.existsByTenantIdAndEstado(tenantId, EstadoReserva.CONFIRMADA)) {
            throw new IllegalArgumentException(
                    "Tienes reservas confirmadas (con seña registrada): cancélalas — devolviendo la seña — "
                            + "o márcalas realizadas antes de cerrar el negocio");
        }

        // Solicitudes y cotizaciones abiertas: nadie las atenderá — se cancelan con nota.
        List<Reserva> abiertas = reservaRepository.findByTenantIdAndEstadoIn(
                tenantId, List.of(EstadoReserva.PENDIENTE, EstadoReserva.COTIZADA));
        for (Reserva reserva : abiertas) {
            reserva.transicionarA(EstadoReserva.CANCELADA);
            String previos = reserva.getComentarios() == null ? "" : reserva.getComentarios() + "\n";
            reserva.setComentarios(previos + "[Cierre del negocio] Cancelada al cerrar la cuenta.");
            bloqueRepository.transicionarEstado(reserva.getBloqueId(), tenantId,
                    EstadoBloque.EN_ESPERA, EstadoBloque.DISPONIBLE);
        }

        tenant.cerrar(OffsetDateTime.now(clock));
        // Todas las sesiones del tenant mueren; el login queda bloqueado por estado.
        for (Usuario usuario : usuarioRepository.findByTenantId(tenantId)) {
            refreshTokenRepository.revocarTodosDeUsuario(usuario.getId());
        }
        log.info("Offboarding: tenant '{}' (#{}) cerrado por su dueño; {} solicitudes abiertas canceladas",
                tenant.getSlug(), tenantId, abiertas.size());
        // Otra bean: el proxy aplica y se une a esta transacción → refleja el estado final.
        return tenantExportService.exportar(tenantId);
    }

    private Tenant buscar(Long tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Negocio no encontrado"));
    }
}
