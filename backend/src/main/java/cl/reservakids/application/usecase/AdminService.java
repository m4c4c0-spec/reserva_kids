package cl.reservakids.application.usecase;

import cl.reservakids.application.dto.AdminDtos.NegocioAdminResumen;
import cl.reservakids.domain.model.Tenant;
import cl.reservakids.domain.repository.NegocioAdminView;
import cl.reservakids.domain.repository.RefreshTokenRepository;
import cl.reservakids.domain.repository.ReservaRepository;
import cl.reservakids.domain.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Gobierno de negocios por el administrador de plataforma (F2): listar, ver y cambiar el estado
 * de los tenants. Reusa la semántica de {@link Tenant#estado} (ACTIVO/SUSPENDIDO/CERRADO) que ya
 * gobierna la visibilidad pública y el login (ver {@code AuthService.verificarTenantOperativo}).
 *
 * Cross-tenant por diseño: el admin NO tiene tenant; estas operaciones tocan cualquier negocio.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final TenantRepository tenantRepository;
    private final ReservaRepository reservaRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    /**
     * Listado para la consola, filtrable por estado y texto (nombre/slug). Un solo query agregado.
     * El patrón LIKE se arma aquí (minúsculas + comodines) y se pasa ya tipado como texto: pasar
     * el término crudo dentro de un CONCAT en HQL hacía que Postgres infiriera el bind como bytea
     * cuando era null → "function lower(bytea) does not exist".
     */
    @Transactional(readOnly = true)
    public List<NegocioAdminResumen> listarNegocios(String estado, String q) {
        String estadoFiltro = (estado == null || estado.isBlank()) ? null : estado.trim().toUpperCase();
        String patron = (q == null || q.isBlank()) ? null : "%" + q.trim().toLowerCase() + "%";
        return tenantRepository.listarParaAdmin(estadoFiltro, patron).stream()
                .map(AdminService::aResumen)
                .toList();
    }

    @Transactional(readOnly = true)
    public NegocioAdminResumen detalle(Long tenantId) {
        Tenant t = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Negocio no encontrado"));
        long reservas = reservaRepository.countByTenantId(tenantId);
        return new NegocioAdminResumen(
                t.getId(), t.getSlug(), t.getNombre(), t.getPlan(), t.getEstado(), t.getCreadoEn(), reservas);
    }

    /**
     * Suspende un negocio: ACTIVO → SUSPENDIDO. La página pública desaparece (el finder público
     * filtra por ACTIVO) y se revocan las sesiones vivas de sus usuarios (no basta con bloquear
     * logins nuevos). Idempotente si ya está SUSPENDIDO. Un negocio CERRADO no se puede suspender.
     */
    @Transactional
    public NegocioAdminResumen suspender(Long tenantId) {
        Tenant t = cargar(tenantId);
        if (Tenant.ESTADO_CERRADO.equals(t.getEstado())) {
            throw new IllegalArgumentException("No se puede suspender un negocio cerrado");
        }
        if (!Tenant.ESTADO_SUSPENDIDO.equals(t.getEstado())) {
            t.setEstado(Tenant.ESTADO_SUSPENDIDO);
            refreshTokenRepository.revocarTodosDeTenant(tenantId);
            log.info("Admin: negocio '{}' (#{}) SUSPENDIDO; sesiones revocadas", t.getSlug(), tenantId);
        }
        return detalleDe(t);
    }

    /** Reactiva un negocio suspendido: SUSPENDIDO → ACTIVO. Idempotente si ya está ACTIVO. */
    @Transactional
    public NegocioAdminResumen reactivar(Long tenantId) {
        Tenant t = cargar(tenantId);
        if (Tenant.ESTADO_CERRADO.equals(t.getEstado())) {
            throw new IllegalArgumentException("No se puede reactivar un negocio cerrado");
        }
        if (Tenant.ESTADO_SUSPENDIDO.equals(t.getEstado())) {
            t.setEstado(Tenant.ESTADO_ACTIVO);
            log.info("Admin: negocio '{}' (#{}) REACTIVADO", t.getSlug(), tenantId);
        }
        return detalleDe(t);
    }

    private Tenant cargar(Long tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Negocio no encontrado"));
    }

    private NegocioAdminResumen detalleDe(Tenant t) {
        long reservas = reservaRepository.countByTenantId(t.getId());
        return new NegocioAdminResumen(
                t.getId(), t.getSlug(), t.getNombre(), t.getPlan(), t.getEstado(), t.getCreadoEn(), reservas);
    }

    private static NegocioAdminResumen aResumen(NegocioAdminView v) {
        return new NegocioAdminResumen(
                v.getId(), v.getSlug(), v.getNombre(), v.getPlan(), v.getEstado(), v.getCreadoEn(), v.getReservas());
    }
}
