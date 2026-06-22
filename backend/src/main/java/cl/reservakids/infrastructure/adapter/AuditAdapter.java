package cl.reservakids.infrastructure.adapter;

import cl.reservakids.application.usecase.AuditPort;
import cl.reservakids.domain.model.AuditEvent;
import cl.reservakids.domain.repository.AuditEventRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuditAdapter implements AuditPort {

    private final AuditEventRepository auditEventRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(Long tenantId, Long actorId, String actorType, String accion,
                          String recursoTipo, Long recursoId, String detalle) {
        try {
            String ip = obtenerIp();
            AuditEvent event = new AuditEvent(tenantId, actorId, actorType, accion,
                    recursoTipo, recursoId, truncar(detalle, 1000), ip);
            auditEventRepository.save(event);
        } catch (Exception e) {
            log.error("No se pudo registrar evento de auditoría: accion={} actorType={} tenant={}",
                    accion, actorType, tenantId, e);
        }
    }

    private String obtenerIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            HttpServletRequest request = attrs.getRequest();
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[forwarded.split(",").length - 1].trim();
            }
            return request.getRemoteAddr();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private static String truncar(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen - 3) + "...";
    }
}
