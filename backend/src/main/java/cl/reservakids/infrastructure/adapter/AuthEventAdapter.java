package cl.reservakids.infrastructure.adapter;

import cl.reservakids.application.usecase.AuthEventPort;
import cl.reservakids.domain.model.AuthEvent;
import cl.reservakids.domain.repository.AuthEventRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthEventAdapter implements AuthEventPort {

    private final AuthEventRepository authEventRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(String actorType, Long actorId, String email, String accion,
                          String resultado, String detalle) {
        try {
            String ip = obtenerIp();
            String userAgent = obtenerUserAgent();
            String emailHash = hashEmail(email);
            AuthEvent event = new AuthEvent(actorType, actorId, emailHash, accion,
                    resultado, ip, userAgent, detalle);
            authEventRepository.save(event);
        } catch (Exception e) {
            log.error("No se pudo registrar evento de autenticación: accion={} actorType={}",
                    accion, actorType, e);
        }
    }

    private String hashEmail(String email) {
        if (email == null) return "unknown";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(email.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            return "error";
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

    private String obtenerUserAgent() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            return attrs.getRequest().getHeader("User-Agent");
        } catch (Exception e) {
            return null;
        }
    }
}
