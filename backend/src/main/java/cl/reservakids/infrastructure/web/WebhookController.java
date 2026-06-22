package cl.reservakids.infrastructure.web;

import cl.reservakids.application.usecase.ReservaService;
import cl.reservakids.domain.model.Tenant;
import cl.reservakids.domain.repository.TenantRepository;
import cl.reservakids.infrastructure.security.CredentialCipher;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.core.MPRequestOptions;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.payment.Payment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/api/public/webhooks/mercadopago")
@Slf4j
public class WebhookController {

    private final ReservaService reservaService;
    private final TenantRepository tenantRepository;
    private final CredentialCipher credentialCipher;

    private static final String IGNORADO = "ignorado";

    /** Per-tenant webhook rate limit: evita que un atacante fuerce llamadas a la API de MP iterando tenantIds. */
    private static final int MAX_WEBHOOKS_TENANT_POR_MINUTO = 10;
    private final Map<Long, WebhookBucket> contadorPorTenant = new ConcurrentHashMap<>();

    /** Ventana de validez de la firma del webhook en segundos — anti-replay. */
    @Value("${app.mercadopago.webhook-signature-max-age-minutes:5}")
    private int signatureMaxAgeMinutes;

    public WebhookController(ReservaService reservaService,
                             TenantRepository tenantRepository,
                             CredentialCipher credentialCipher) {
        this.reservaService = reservaService;
        this.tenantRepository = tenantRepository;
        this.credentialCipher = credentialCipher;
    }

    private record WebhookBucket(long epochMinuto, AtomicInteger contador) {}

    @PostMapping("/{tenantId}")
    public ResponseEntity<String> recibirWebhook(
            @PathVariable Long tenantId,
            @RequestParam(required = false) String topic,
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "id", required = false) String id,
            @RequestParam(name = "data.id", required = false) String dataIdParam,
            @RequestHeader(name = "x-signature", required = false) String xSignature,
            @RequestHeader(name = "x-request-id", required = false) String xRequestId,
            @RequestBody(required = false) Map<String, Object> body) {

        log.debug("Webhook MP para tenant {}: topic={}, type={}, id={}", tenantId, topic, type, id);

        String tipo = resolverTipo(topic, type, body);
        String dataId = resolverDataId(dataIdParam, id, body);

        if (!"payment".equals(tipo) || dataId == null) {
            return ResponseEntity.ok(IGNORADO);
        }
        Long paymentId = parsearLongOrNull(dataId);
        if (paymentId == null) {
            log.warn("Webhook MP con data.id no numérico: {}", dataId);
            return ResponseEntity.ok(IGNORADO);
        }

        if (rateLimitExcedido(tenantId)) {
            log.warn("Webhook MP tenant {}: rate limit excedido — rechazado", tenantId);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body("Demasiadas solicitudes");
        }

        try {
            return procesarEventoPago(tenantId, paymentId, dataId, xRequestId, xSignature);
        } catch (MPException | MPApiException e) {
            log.error("Error al procesar webhook de MP: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body("Error interno");
        }
    }

    private boolean rateLimitExcedido(Long tenantId) {
        long minutoActual = Instant.now().getEpochSecond() / 60;
        WebhookBucket bucket = contadorPorTenant.compute(tenantId, (k, v) -> {
            if (v == null || v.epochMinuto() != minutoActual) {
                return new WebhookBucket(minutoActual, new AtomicInteger());
            }
            return v;
        });
        if (contadorPorTenant.size() > 500) {
            contadorPorTenant.entrySet().removeIf(e -> e.getValue().epochMinuto() != minutoActual);
        }
        return bucket.contador().incrementAndGet() > MAX_WEBHOOKS_TENANT_POR_MINUTO;
    }

    private static String resolverTipo(String topic, String type, Map<String, Object> body) {
        if (topic != null) {
            return topic;
        }
        if (type != null) {
            return type;
        }
        if (body == null) {
            return null;
        }
        Object t = body.get("type");
        return (String) (t != null ? t : body.get("topic"));
    }

    private static String resolverDataId(String dataIdParam, String id, Map<String, Object> body) {
        String dataId = dataIdParam != null ? dataIdParam : id;
        if (dataId != null || body == null) {
            return dataId;
        }
        if (body.get("data") instanceof Map<?, ?> data && data.get("id") != null) {
            return String.valueOf(data.get("id"));
        }
        return null;
    }

    private ResponseEntity<String> procesarEventoPago(Long tenantId, long paymentId, String dataId,
                                                       String xRequestId, String xSignature)
            throws MPException, MPApiException {
        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        if (tenant == null || !"ACTIVO".equals(tenant.getEstado())) {
            log.warn("Webhook MP: tenant {} no encontrado o no ACTIVO", tenantId);
            return ResponseEntity.badRequest().body("Configuracion MP no encontrada");
        }
        if (tenant.getMpAccessToken() == null) {
            log.warn("Webhook MP: tenant {} sin MP access token", tenantId);
            return ResponseEntity.badRequest().body("Configuracion MP no encontrada");
        }

        if (tenant.getMpWebhookSecret() != null) {
            String secreto = credentialCipher.decrypt(tenant.getMpWebhookSecret());
            if (!firmaValida(secreto, dataId, xRequestId, xSignature)) {
                log.warn("Webhook MP tenant {}: firma x-signature inválida — rechazado", tenantId);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Firma inválida");
            }
        }

        MPRequestOptions requestOptions = MPRequestOptions.builder()
                .accessToken(credentialCipher.decrypt(tenant.getMpAccessToken()))
                .build();
        Payment payment = new PaymentClient().get(paymentId, requestOptions);

        if (payment.getTransactionAmount() == null || payment.getExternalReference() == null) {
            log.warn("Pago {} sin transaction_amount o sin external_reference", dataId);
            return ResponseEntity.ok(IGNORADO);
        }
        Long reservaId = parsearLongOrNull(payment.getExternalReference());
        if (reservaId == null) {
            log.warn("Pago {} con external_reference no numérica: {}", dataId, payment.getExternalReference());
            return ResponseEntity.ok(IGNORADO);
        }
        reservaService.procesarWebhookPago(tenantId, reservaId, dataId,
                payment.getTransactionAmount().intValue(), payment.getStatus(), "MERCADOPAGO");
        return ResponseEntity.ok("ok");
    }

    private static Long parsearLongOrNull(String valor) {
        try {
            return Long.parseLong(valor);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean firmaValida(String secreto, String dataId, String xRequestId, String xSignature) {
        if (secreto == null || secreto.isBlank() || xSignature == null) {
            return false;
        }
        String ts = extraerParte(xSignature, "ts");
        String v1 = extraerParte(xSignature, "v1");
        if (ts == null || v1 == null) {
            return false;
        }

        long tsEpoch;
        try {
            tsEpoch = Long.parseLong(ts);
        } catch (NumberFormatException e) {
            return false;
        }
        long ahora = Instant.now().getEpochSecond();
        long edadSegundos = ahora - tsEpoch;
        if (edadSegundos < 0) {
            return false;
        }
        if (edadSegundos > (long) signatureMaxAgeMinutes * 60) {
            log.warn("Webhook MP: firma expirada ({}s de edad, max {} min)", edadSegundos, signatureMaxAgeMinutes);
            return false;
        }

        String manifiesto = "id:" + dataId.toLowerCase() + ";request-id:"
                + (xRequestId == null ? "" : xRequestId) + ";ts:" + ts + ";";
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String esperado = HexFormat.of().formatHex(
                    mac.doFinal(manifiesto.getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(
                    esperado.getBytes(StandardCharsets.UTF_8), v1.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("Error validando firma del webhook: {}", e.getMessage());
            return false;
        }
    }

    private static String extraerParte(String xSignature, String clave) {
        for (String parte : xSignature.split(",")) {
            String[] kv = parte.split("=", 2);
            if (kv.length == 2 && kv[0].trim().equals(clave)) {
                return kv[1].trim();
            }
        }
        return null;
    }
}
