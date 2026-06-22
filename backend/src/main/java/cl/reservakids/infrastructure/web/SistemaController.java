package cl.reservakids.infrastructure.web;

import cl.reservakids.application.dto.AuditEventDtos.AuditEventResponse;
import cl.reservakids.application.dto.MetricasDtos.DashboardResponse;
import cl.reservakids.application.usecase.MetricsService;
import cl.reservakids.domain.model.AuditEvent;
import cl.reservakids.domain.model.Tenant;
import cl.reservakids.domain.repository.AuditEventRepository;
import cl.reservakids.domain.repository.ServicioRepository;
import cl.reservakids.domain.repository.TenantRepository;
import cl.reservakids.infrastructure.adapter.NotificacionAdapter;
import cl.reservakids.infrastructure.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Falla #3 (revisión a 2 años): estado del canal de notificaciones, consultado por el panel.
 * Si el email lleva fallos consecutivos, el frontend muestra un aviso — sin esto la
 * degradación "elegante" del SMTP era invisible y el dueño perdía solicitudes en silencio.
 */
@RestController
@RequestMapping("/api/sistema")
@RequiredArgsConstructor
public class SistemaController {

    private final NotificacionAdapter notificacionAdapter;
    private final AuditEventRepository auditEventRepository;
    private final MetricsService metricsService;
    private final TenantRepository tenantRepository;
    private final ServicioRepository servicioRepository;

    /**
     * V27: estado del onboarding del dueño. El panel lo consulta al entrar y, si faltan pasos,
     * muestra un globo gigante "¡Bienvenido! Hagamos 2 cosas para empezar a vender" con botones
     * que llevan directo a crear el primer servicio y a configurar la pasarela de pago.
     * Sirve también de "lista de verificación" siempre visible hasta completar todo.
     */
    @GetMapping("/onboarding")
    public Map<String, Object> onboarding(@AuthenticationPrincipal AuthPrincipal principal) {
        Tenant tenant = tenantRepository.findById(principal.tenantId()).orElseThrow();
        long serviciosActivos = servicioRepository.findByTenantIdAndActivoTrueOrderByNombre(tenant.getId()).size();
        // "Configuraste la pasarela" = algo concreto para empezar a recibir señas online.
        boolean pasarelaConfigurada = tenant.tienePasarelaConfigurada();
        boolean contactoConfigurado = tenant.getTelefonoContacto() != null
                && !tenant.getTelefonoContacto().isBlank();
        boolean completo = serviciosActivos > 0 && pasarelaConfigurada;
        return Map.of(
                "servicios", serviciosActivos,
                "pasarelaConfigurada", pasarelaConfigurada,
                "contactoConfigurado", contactoConfigurado,
                "completo", completo);
    }

    @GetMapping("/notificaciones")
    public NotificacionAdapter.EstadoEnvios estadoNotificaciones() {
        return notificacionAdapter.estadoEnvios();
    }

    /**
     * Dashboard de métricas para el panel del dueño: ingresos mensuales,
     * servicios más vendidos y tasa de ocupación.
     */
    @GetMapping("/dashboard")
    public DashboardResponse dashboard(@AuthenticationPrincipal AuthPrincipal principal) {
        return metricsService.dashboard(principal.tenantId());
    }

    /**
     * RNF-07: trazabilidad de acciones del panel del dueño (append-only).
     * El DUENO consulta su propia bitácora, acotada a su tenant.
     */
    @GetMapping("/auditoria")
    public List<AuditEventResponse> auditoria(@AuthenticationPrincipal AuthPrincipal principal,
                                              @RequestParam(defaultValue = "50") int limite) {
        int n = Math.min(Math.max(limite, 1), 200);
        return auditEventRepository.findByTenantIdOrderByCreadoEnDesc(principal.tenantId(),
                PageRequest.of(0, n)).stream()
                .map(e -> new AuditEventResponse(e.getCreadoEn(), e.getAccion(),
                        e.getRecursoTipo(), e.getRecursoId(), e.getDetalle(), e.getActorType()))
                .toList();
    }
}
