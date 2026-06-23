package cl.reservakids.infrastructure.web;

import cl.reservakids.application.dto.AuditEventDtos.AuditEventResponse;
import cl.reservakids.application.dto.CajaDtos.CajaDiariaResponse;
import cl.reservakids.application.dto.MetricasDtos.DashboardResponse;
import cl.reservakids.application.usecase.CajaService;
import cl.reservakids.application.usecase.MetricsService;
import cl.reservakids.application.usecase.WebhookFailureMonitor;
import cl.reservakids.domain.model.AuditEvent;
import cl.reservakids.domain.model.Tenant;
import cl.reservakids.domain.repository.AuditEventRepository;
import cl.reservakids.domain.repository.HorarioAtencionRepository;
import cl.reservakids.domain.repository.ServicioRepository;
import cl.reservakids.domain.repository.TenantRepository;
import cl.reservakids.infrastructure.adapter.NotificacionAdapter;
import cl.reservakids.infrastructure.adapter.WhatsappStubAdapter;
import cl.reservakids.infrastructure.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
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
    private final WhatsappStubAdapter whatsappAdapter;
    private final WebhookFailureMonitor webhookFailureMonitor;
    private final AuditEventRepository auditEventRepository;
    private final MetricsService metricsService;
    private final TenantRepository tenantRepository;
    private final ServicioRepository servicioRepository;
    private final HorarioAtencionRepository horarioAtencionRepository;
    private final CajaService cajaService;

    @GetMapping("/caja")
    public CajaDiariaResponse caja(@AuthenticationPrincipal AuthPrincipal principal,
                                    @RequestParam String fecha) {
        return cajaService.cajaDiaria(principal.tenantId(), fecha);
    }

    /**
     * V27: estado del onboarding del dueño. El panel lo consulta al entrar y, si faltan pasos,
     * muestra un globo gigante "¡Bienvenido! Hagamos 2 cosas para empezar a vender" con botones
     * que llevan directo a crear el primer servicio y a configurar la pasarela de pago.
     * Sirve también de "lista de verificación" siempre visible hasta completar cada paso.
     */
    @GetMapping("/onboarding")
    public Map<String, Object> onboarding(@AuthenticationPrincipal AuthPrincipal principal) {
        Tenant tenant = tenantRepository.findById(principal.tenantId()).orElseThrow();
        long serviciosActivos = servicioRepository.findByTenantIdAndActivoTrueOrderByNombre(tenant.getId()).size();
        // Sin franjas de atención no hay horas reservables → es un paso bloqueante del onboarding.
        boolean horariosConfigurados = horarioAtencionRepository.existsByTenantId(tenant.getId());
        // "Configuraste la pasarela" = algo concreto para empezar a recibir señas online.
        boolean pasarelaConfigurada = tenant.tienePasarelaConfigurada();
        boolean contactoConfigurado = tenant.getTelefonoContacto() != null
                && !tenant.getTelefonoContacto().isBlank();
        boolean completo = serviciosActivos > 0 && horariosConfigurados && pasarelaConfigurada;
        return Map.of(
                "servicios", serviciosActivos,
                "horariosConfigurados", horariosConfigurados,
                "pasarelaConfigurada", pasarelaConfigurada,
                "contactoConfigurado", contactoConfigurado,
                "completo", completo);
    }

    @GetMapping("/notificaciones")
    public Map<String, Object> estadoNotificaciones() {
        NotificacionAdapter.EstadoEnvios email = notificacionAdapter.estadoEnvios();
        Map<String, Object> estado = new LinkedHashMap<>();
        estado.put("email", Map.of(
                "fallosConsecutivos", email.fallosConsecutivos(),
                "smtpConfigurado", email.smtpConfigurado(),
                "ultimoFalloEn", email.ultimoFalloEn() != null ? email.ultimoFalloEn().toString() : null));
        estado.put("whatsapp", Map.of(
                "fallosConsecutivos", whatsappAdapter.fallosConsecutivos(),
                "habilitado", whatsappAdapter.isHabilitadoYConfigurado()));
        estado.put("webhooks", Map.of(
                "mercadopago", webhookFailureMonitor.fallosConsecutivos("Mercado Pago"),
                "khipu", webhookFailureMonitor.fallosConsecutivos("Khipu")));
        return estado;
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
