package cl.reservakids.application.usecase;

import cl.reservakids.application.dto.AdminDtos.AdminResumen;
import cl.reservakids.application.dto.AdminDtos.AuditoriaItem;
import cl.reservakids.application.dto.AdminDtos.CrearAdminRequest;
import cl.reservakids.application.dto.AdminDtos.MetricasGlobales;
import cl.reservakids.application.dto.AdminDtos.NegocioAdminResumen;
import cl.reservakids.domain.model.AdminAuditLog;
import cl.reservakids.domain.model.Administrador;
import cl.reservakids.domain.model.EstadoReserva;
import cl.reservakids.domain.model.Tenant;
import cl.reservakids.domain.repository.AdminAuditLogRepository;
import cl.reservakids.domain.repository.AdministradorRepository;
import cl.reservakids.domain.repository.AuditoriaView;
import cl.reservakids.domain.repository.CuentaClienteRepository;
import cl.reservakids.domain.repository.NegocioAdminView;
import cl.reservakids.domain.repository.PagoRepository;
import cl.reservakids.domain.repository.RefreshTokenRepository;
import cl.reservakids.domain.repository.ReservaRepository;
import cl.reservakids.domain.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

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
    private final CuentaClienteRepository cuentaClienteRepository;
    private final PagoRepository pagoRepository;
    private final AdministradorRepository administradorRepository;
    private final AdminAuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;

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

    /** KPIs globales de plataforma (F4): visión de operador. Todo cross-tenant. */
    @Transactional(readOnly = true)
    public MetricasGlobales metricas() {
        return new MetricasGlobales(
                tenantRepository.count(),
                tenantRepository.countByEstado(Tenant.ESTADO_ACTIVO),
                tenantRepository.countByEstado(Tenant.ESTADO_SUSPENDIDO),
                tenantRepository.countByEstado(Tenant.ESTADO_CERRADO),
                reservaRepository.count(),
                reservaRepository.countByEstadoIn(EstadoReserva.ACTIVOS),
                cuentaClienteRepository.count(),
                pagoRepository.totalRecaudadoPlataforma());
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
    public NegocioAdminResumen suspender(Long tenantId, Long actorId) {
        Tenant t = cargar(tenantId);
        if (Tenant.ESTADO_CERRADO.equals(t.getEstado())) {
            throw new IllegalArgumentException("No se puede suspender un negocio cerrado");
        }
        if (!Tenant.ESTADO_SUSPENDIDO.equals(t.getEstado())) {
            t.setEstado(Tenant.ESTADO_SUSPENDIDO);
            refreshTokenRepository.revocarTodosDeTenant(tenantId);
            auditar(actorId, AdminAuditLog.SUSPENDER_NEGOCIO, tenantId, t.getNombre() + " (/" + t.getSlug() + ")");
            log.info("Admin #{}: negocio '{}' (#{}) SUSPENDIDO; sesiones revocadas", actorId, t.getSlug(), tenantId);
        }
        return detalleDe(t);
    }

    /** Reactiva un negocio suspendido: SUSPENDIDO → ACTIVO. Idempotente si ya está ACTIVO. */
    @Transactional
    public NegocioAdminResumen reactivar(Long tenantId, Long actorId) {
        Tenant t = cargar(tenantId);
        if (Tenant.ESTADO_CERRADO.equals(t.getEstado())) {
            throw new IllegalArgumentException("No se puede reactivar un negocio cerrado");
        }
        if (Tenant.ESTADO_SUSPENDIDO.equals(t.getEstado())) {
            t.setEstado(Tenant.ESTADO_ACTIVO);
            auditar(actorId, AdminAuditLog.REACTIVAR_NEGOCIO, tenantId, t.getNombre() + " (/" + t.getSlug() + ")");
            log.info("Admin #{}: negocio '{}' (#{}) REACTIVADO", actorId, t.getSlug(), tenantId);
        }
        return detalleDe(t);
    }

    // ── F5: gestión multi-admin + bitácora ──

    /** Alta de otro administrador (admin→admin). Email único, normalizado; nunca expone el hash. */
    @Transactional
    public AdminResumen crearAdmin(CrearAdminRequest req, Long actorId) {
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        if (administradorRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Ya existe un administrador con ese email");
        }
        Administrador nuevo = new Administrador();
        nuevo.setEmail(email);
        nuevo.setNombre(req.nombre().trim());
        nuevo.setPasswordHash(passwordEncoder.encode(req.password()));
        nuevo = administradorRepository.save(nuevo);
        auditar(actorId, AdminAuditLog.CREAR_ADMIN, null, "Alta de admin " + email);
        log.info("Admin #{}: alta de administrador {}", actorId, email);
        return new AdminResumen(nuevo.getId(), nuevo.getEmail(), nuevo.getNombre(), nuevo.getCreadoEn());
    }

    @Transactional(readOnly = true)
    public List<AdminResumen> listarAdmins() {
        return administradorRepository.findAll().stream()
                .map(a -> new AdminResumen(a.getId(), a.getEmail(), a.getNombre(), a.getCreadoEn()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AuditoriaItem> auditoria(int limite) {
        int n = Math.min(Math.max(limite, 1), 200);
        return auditLogRepository.listarReciente(PageRequest.of(0, n)).stream()
                .map(v -> new AuditoriaItem(v.getCreadoEn(), v.getAdminEmail(), v.getAccion(), v.getDetalle()))
                .toList();
    }

    private void auditar(Long actorId, String accion, Long tenantId, String detalle) {
        auditLogRepository.save(new AdminAuditLog(actorId, accion, tenantId, detalle));
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
