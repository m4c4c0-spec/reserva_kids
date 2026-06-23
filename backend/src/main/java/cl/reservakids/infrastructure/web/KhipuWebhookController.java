package cl.reservakids.infrastructure.web;

import cl.reservakids.application.usecase.ReservaService;
import cl.reservakids.application.usecase.WebhookFailureMonitor;
import cl.reservakids.domain.model.Tenant;
import cl.reservakids.domain.repository.TenantRepository;
import cl.reservakids.infrastructure.adapter.KhipuAdapter;
import cl.reservakids.infrastructure.security.CredentialCipher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

/**
 * V25: notificaciones de Khipu (API de notificaciones 3.0). Recibimos un POST con el cuerpo
 * del pago + cabecera {@code x-khipu-signature: t=<ms>,s=<base64 hmac>}; validamos el HMAC
 * con el mismo API key del tenant (en v3 el x-api-key también firma el webhook) y pedimos el
 * estado canónico con GET /payments/{id} antes de marcar la seña como pagada. Igual que en el
 * webhook de MP, registramos el pago y confirmamos la reserva solo ante "approved" ("done" en
 * Khipu). La idempotencia la garantiza {@code pagoRepository.existsByReferenciaExterna}.
 */
@RestController
@RequestMapping("/api/public/webhooks/khipu")
@Slf4j
public class KhipuWebhookController {

    private final ReservaService reservaService;
    private final TenantRepository tenantRepository;
    private final CredentialCipher credentialCipher;
    private final KhipuAdapter khipuAdapter;
    private final WebhookFailureMonitor failureMonitor;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String IGNORADO = "ignorado";
    private static final String CANAL_KHIPU = "Khipu";

    @Value("${app.khipu.webhook-signature-max-age-minutes:5}")
    private int signatureMaxAgeMinutes;

    public KhipuWebhookController(ReservaService reservaService, TenantRepository tenantRepository,
                                   CredentialCipher credentialCipher, KhipuAdapter khipuAdapter,
                                   WebhookFailureMonitor failureMonitor) {
        this.reservaService = reservaService;
        this.tenantRepository = tenantRepository;
        this.credentialCipher = credentialCipher;
        this.khipuAdapter = khipuAdapter;
        this.failureMonitor = failureMonitor;
    }

    @PostMapping("/{tenantId}")
    public ResponseEntity<String> recibir(HttpServletRequest request, @PathVariable Long tenantId) {
        byte[] raw;
        try {
            raw = request.getInputStream().readAllBytes();
        } catch (Exception e) {
            log.error("Khipu webhook tenant {}: no se pudo leer el body", tenantId);
            failureMonitor.registrarFallo(CANAL_KHIPU, e.getMessage());
            return ResponseEntity.badRequest().body("body inválido");
        }

        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        if (tenant == null || !Tenant.ESTADO_ACTIVO.equals(tenant.getEstado())
                || tenant.getKhipuApiKey() == null) {
            log.warn("Khipu webhook: tenant {} no encontrado, no ACTIVO o sin API key", tenantId);
            failureMonitor.registrarFallo(CANAL_KHIPU, "Tenant no configurado");
            return ResponseEntity.badRequest().body("tenant no configurado");
        }

        String secreto = credentialCipher.decrypt(tenant.getKhipuApiKey());
        if (!firmaValida(secreto, request.getHeader("x-khipu-signature"), raw)) {
            log.warn("Khipu webhook tenant {}: firma x-khipu-signature inválida — rechazado", tenantId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("firma inválida");
        }

        JsonNode body;
        try {
            body = objectMapper.readTree(raw);
        } catch (Exception e) {
            log.warn("Khipu webhook tenant {}: body no es JSON válido", tenantId);
            failureMonitor.registrarFallo(CANAL_KHIPU, e.getMessage());
            return ResponseEntity.badRequest().body("json inválido");
        }

        String paymentId = body.path("payment_id").asText(null);
        if (paymentId == null || paymentId.isBlank()) {
            return ResponseEntity.ok(IGNORADO);
        }

        // Estado canónico vía GET (no nos fiamos solo del body del webhook).
        KhipuAdapter.KhipuPago pago;
        try {
            pago = khipuAdapter.obtenerPago(paymentId, tenant).orElse(null);
        } catch (Exception e) {
            log.error("Error consultando pago Khipu {} para tenant {}: {}", paymentId, tenantId, e.getMessage());
            failureMonitor.registrarFallo(CANAL_KHIPU, e.getMessage());
            return ResponseEntity.internalServerError().body("no se pudo consultar el pago");
        }
        if (pago == null) {
            log.warn("Khipu webhook: no se pudo obtener el pago {}", paymentId);
            failureMonitor.registrarFallo(CANAL_KHIPU, "Pago no encontrado: " + paymentId);
            return ResponseEntity.internalServerError().body("no se pudo consultar el pago");
        }

        // La API de Khipu respondió → el canal está sano
        failureMonitor.registrarExito(CANAL_KHIPU);

        if (!"done".equals(pago.status())) {
            log.info("Khipu webhook: pago {} con estado '{}' (no confirma)", paymentId, pago.status());
            return ResponseEntity.ok(IGNORADO);
        }

        Long reservaId = parsearLongOrNull(pago.transaction_id());
        if (reservaId == null) {
            // transaction_id es el que enviamos al crear (= reserva.id). Sin él no hay forma segura.
            log.warn("Khipu webhook: pago {} sin transaction_id válido ({})", paymentId, pago.transaction_id());
            return ResponseEntity.ok(IGNORADO);
        }
        Integer monto = pago.amount() == null ? null : pago.amount().intValue();

        // Khipu "done" ≡ MP "approved": registramos la seña y confirmamos la reserva.
        reservaService.procesarWebhookPago(tenantId, reservaId, paymentId, monto, "approved", "KHIPU");
        return ResponseEntity.ok("ok");
    }

    private boolean firmaValida(String secreto, String xKhipuSignature, byte[] rawBody) {
        if (secreto == null || secreto.isBlank() || xKhipuSignature == null) {
            return false;
        }
        String t = null;
        String s = null;
        for (String parte : xKhipuSignature.split(",")) {
            String[] kv = parte.split("=", 2);
            if (kv.length == 2) {
                if (kv[0].trim().equals("t")) t = kv[1].trim();
                else if (kv[0].trim().equals("s")) s = kv[1].trim();
            }
        }
        if (t == null || s == null) {
            return false;
        }
        long ts;
        try {
            ts = Long.parseLong(t);
        } catch (NumberFormatException e) {
            return false;
        }
        long ahoraMs = Instant.now().toEpochMilli();
        long edadMs = ahoraMs - ts;
        if (edadMs < 0 || edadMs > (long) signatureMaxAgeMinutes * 60_000) {
            log.warn("Khipu webhook: firma expirada o futura ({}ms de edad, max {} min)", edadMs, signatureMaxAgeMinutes);
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            // Manifiesto: "<timestamp>.<payload crudo sin tocar>"
            byte[] toHash = (t + "." + new String(rawBody, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8);
            String esperado = Base64.getEncoder().encodeToString(mac.doFinal(toHash));
            return MessageDigest.isEqual(
                    esperado.getBytes(StandardCharsets.UTF_8), s.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("Error validando firma de Khipu: {}", e.getMessage());
            return false;
        }
    }

    private static Long parsearLongOrNull(String valor) {
        try {
            return Long.parseLong(valor);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}