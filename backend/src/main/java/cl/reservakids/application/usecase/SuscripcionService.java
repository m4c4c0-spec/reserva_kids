package cl.reservakids.application.usecase;

import cl.reservakids.application.dto.SuscripcionDtos.*;
import cl.reservakids.domain.exception.RecursoNoEncontradoException;
import cl.reservakids.domain.model.SuscripcionPago;
import cl.reservakids.domain.model.Tenant;
import cl.reservakids.domain.repository.SuscripcionPagoRepository;
import cl.reservakids.domain.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SuscripcionService {

    private final TenantRepository tenantRepository;
    private final SuscripcionPagoRepository suscripcionPagoRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public SuscripcionResponse estadoSuscripcion(Long tenantId) {
        Tenant t = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Negocio no encontrado"));
        return new SuscripcionResponse(
                t.getPlanSuscripcion(), t.getSuscripcionEstado(),
                null, null,
                t.getSuscripcionInicio(), t.getSuscripcionRenovacion(),
                t.getSuscripcionReferenciaExterna());
    }

    @Transactional(readOnly = true)
    public List<PlanResponse> listarPlanes() {
        return PlanResponse.PLANES;
    }

    @Transactional
    public SuscripcionPagoResponse contratar(Long tenantId, SuscripcionRequest req) {
        Tenant t = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Negocio no encontrado"));

        PlanResponse plan = PlanResponse.PLANES.stream()
                .filter(p -> p.codigo().equals(req.plan()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Plan no encontrado: " + req.plan()));

        int monto = "ANUAL".equals(req.periodo()) ? plan.precioAnualClp() : plan.precioMensualClp();
        if (monto <= 0) {
            throw new IllegalArgumentException("El plan GRATIS no requiere pago");
        }

        SuscripcionPago pago = new SuscripcionPago(tenantId, req.plan(), monto, req.periodo());
        pago = suscripcionPagoRepository.save(pago);

        // En producción, aquí se integraría con Mercado Pago Subscriptions o Stripe
        // para generar el cargo recurrente. Por ahora, se registra el pago como
        // PENDIENTE y el admin lo aprueba manualmente desde la consola.
        t.setPlanSuscripcion(req.plan());
        if (t.getSuscripcionInicio() == null) {
            t.setSuscripcionInicio(OffsetDateTime.now(clock));
        }
        if ("MENSUAL".equals(req.periodo())) {
            t.setSuscripcionRenovacion(OffsetDateTime.now(clock).plusMonths(1));
        } else {
            t.setSuscripcionRenovacion(OffsetDateTime.now(clock).plusYears(1));
        }

        return new SuscripcionPagoResponse(pago.getId(), pago.getMontoClp(),
                pago.getEstado(), pago.getPeriodo(), pago.getFecha(), pago.getReferenciaExterna());
    }

    @Transactional(readOnly = true)
    public List<SuscripcionPagoResponse> historial(Long tenantId) {
        return suscripcionPagoRepository.findByTenantIdOrderByFechaDesc(tenantId)
                .stream()
                .map(p -> new SuscripcionPagoResponse(p.getId(), p.getMontoClp(),
                        p.getEstado(), p.getPeriodo(), p.getFecha(), p.getReferenciaExterna()))
                .toList();
    }

    @Transactional
    public SuscripcionPagoResponse aprobarPago(Long pagoId) {
        SuscripcionPago pago = suscripcionPagoRepository.findById(pagoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Pago no encontrado"));
        pago.setEstado(SuscripcionPago.ESTADO_APROBADO);
        return new SuscripcionPagoResponse(pago.getId(), pago.getMontoClp(),
                pago.getEstado(), pago.getPeriodo(), pago.getFecha(), pago.getReferenciaExterna());
    }
}
