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
 * Offboarding de tenant (falla 3.3, revisión a 5 años): un negocio que se va
 * se lleva sus datos (export JSON) y deja de existir de verdad (cierre + purga diferida).
 * <ul>
 *   <li>Export: copia completa del tenant — portabilidad (Ley 21.719) y a la vez el
 *       insumo del cierre responsable del servicio completo (falla 5.1).</li>
 *   <li>Cierre: PENDIENTE/COTIZADA se cancelan (nadie las atenderá); las CONFIRMADA
 *       (hay seña de por medio: devolver dinero es decisión humana) exigen resolución
 *       manual previa. La página pública desaparece al instante (el finder público
 *       filtra por estado ACTIVO) y las sesiones se revocan.</li>
 *   <li>La purga física diferida corre en {@link ExpiracionService#purgarTenantsCerrados()}.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;
    private final ServicioRepository servicioRepository;
    private final ClienteRepository clienteRepository;
    private final BloqueDisponibleRepository bloqueRepository;
    private final ReservaRepository reservaRepository;
    private final PagoRepository pagoRepository;
    private final UsuarioRepository usuarioRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final Clock clock;

    /** Export completo del tenant. Disponible siempre, no solo al cerrar. */
    @Transactional(readOnly = true)
    public ExportResponse exportar(Long tenantId) {
        Tenant tenant = buscar(tenantId);
        return new ExportResponse(
                tenant.getNombre(), tenant.getSlug(), tenant.getPlan(), tenant.getEstado(),
                tenant.getCreadoEn(), tenant.getCerradoEn(), OffsetDateTime.now(clock),
                servicioRepository.findByTenantIdOrderByNombre(tenantId).stream().map(ServicioExport::de).toList(),
                clienteRepository.findByTenantIdOrderByNombre(tenantId).stream().map(ClienteExport::de).toList(),
                bloqueRepository.findByTenantIdOrderByFechaAscHoraInicioAsc(tenantId).stream().map(BloqueExport::de).toList(),
                reservaRepository.findByTenantIdOrderByCreadaEnDesc(tenantId).stream().map(ReservaExport::de).toList(),
                pagoRepository.findDeTenant(tenantId).stream().map(PagoExport::de).toList());
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
        return exportar(tenantId); // misma transacción: refleja el estado final
    }

    private Tenant buscar(Long tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Negocio no encontrado"));
    }
}
