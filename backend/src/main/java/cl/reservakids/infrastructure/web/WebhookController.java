package cl.reservakids.infrastructure.web;

import cl.reservakids.application.usecase.ReservaService;
import cl.reservakids.domain.model.Tenant;
import cl.reservakids.domain.repository.TenantRepository;
import cl.reservakids.infrastructure.security.CredentialCipher;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.core.MPRequestOptions;
import com.mercadopago.resources.payment.Payment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

@RestController
@RequestMapping("/api/public/webhooks/mercadopago")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final ReservaService reservaService;
    private final TenantRepository tenantRepository;
    private final CredentialCipher credentialCipher;

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

        log.info("Webhook recibido de MP para tenant {}: topic={}, type={}, id={}", tenantId, topic, type, id);

        // MP manda el tipo como 'topic' (IPN v1) o 'type' (webhooks v2), en query o en el body.
        String tipo = topic != null ? topic : type;
        String dataId = dataIdParam != null ? dataIdParam : id;

        if (body != null) {
            if (tipo == null) {
                tipo = (String) body.get("type");
                if (tipo == null && body.containsKey("topic")) {
                    tipo = (String) body.get("topic");
                }
            }
            if (dataId == null && body.containsKey("data")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) body.get("data");
                if (data.containsKey("id")) {
                    dataId = String.valueOf(data.get("id"));
                }
            }
        }

        if (!"payment".equals(tipo) || dataId == null) {
            return ResponseEntity.ok("ignorado"); // Respondemos OK rápido
        }
        // Un id no numérico jamás va a resolverse: responder 200 para que MP no
        // lo reintente eternamente (un 500 aquí = retry loop infinito).
        final long paymentId;
        try {
            paymentId = Long.parseLong(dataId);
        } catch (NumberFormatException e) {
            log.warn("Webhook MP con data.id no numérico: {}", dataId);
            return ResponseEntity.ok("ignorado");
        }

        try {
            Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
            if (tenant == null || tenant.getMpAccessToken() == null) {
                log.warn("Tenant {} no encontrado o sin MP access token", tenantId);
                return ResponseEntity.badRequest().body("Configuracion MP no encontrada");
            }

            // S3: si el tenant configuró el secreto de firma, validamos x-signature ANTES de
            // gastar una llamada a la API de MP. Un webhook forjado se rechaza aquí (401).
            // Sin secreto configurado, se mantiene la defensa anterior: re-consultar el pago
            // a MP con el token del tenant (un id falso no devuelve un pago válido).
            if (tenant.getMpWebhookSecret() != null) {
                String secreto = credentialCipher.decrypt(tenant.getMpWebhookSecret());
                if (!firmaValida(secreto, dataId, xRequestId, xSignature)) {
                    log.warn("Webhook MP tenant {}: firma x-signature inválida — rechazado", tenantId);
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Firma inválida");
                }
            }

            // Consultar el pago real a MP usando el token del tenant.
            // S1: el token está cifrado en reposo — se descifra solo aquí.
            MPRequestOptions requestOptions = MPRequestOptions.builder()
                    .accessToken(credentialCipher.decrypt(tenant.getMpAccessToken()))
                    .build();

            PaymentClient client = new PaymentClient();
            Payment payment = client.get(paymentId, requestOptions);

            String status = payment.getStatus(); // "approved", "pending", "rejected"
            String externalReference = payment.getExternalReference(); // Este es el ID de nuestra reserva

            // Sin monto o sin referencia válida no hay nada que registrar — y un NPE/
            // NumberFormatException aquí terminaba en 500 → MP reintentando para siempre.
            if (payment.getTransactionAmount() == null || externalReference == null) {
                log.warn("Pago {} sin transaction_amount o sin external_reference", dataId);
                return ResponseEntity.ok("ignorado");
            }
            final long reservaId;
            try {
                reservaId = Long.parseLong(externalReference);
            } catch (NumberFormatException e) {
                log.warn("Pago {} con external_reference no numérica: {}", dataId, externalReference);
                return ResponseEntity.ok("ignorado");
            }
            Integer transactionAmount = payment.getTransactionAmount().intValue();
            reservaService.procesarWebhookPago(tenantId, reservaId, dataId, transactionAmount, status);

        } catch (Exception e) {
            log.error("Error al procesar webhook de MP: {}", e.getMessage(), e);
            // Si retornamos 500, MP lo reintentará más tarde
            return ResponseEntity.internalServerError().body("Error interno");
        }

        return ResponseEntity.ok("ok");
    }

    /**
     * S3: valida el header x-signature de Mercado Pago.
     * Formato del header: {@code ts=<timestamp>,v1=<hmac_sha256_hex>}.
     * El manifiesto firmado es {@code id:<data.id>;request-id:<x-request-id>;ts:<ts>;}
     * (HMAC-SHA256 con el secreto de firma del tenant). Comparación en tiempo constante.
     */
    private boolean firmaValida(String secreto, String dataId, String xRequestId, String xSignature) {
        if (secreto == null || secreto.isBlank() || xSignature == null) {
            return false;
        }
        String ts = extraerParte(xSignature, "ts");
        String v1 = extraerParte(xSignature, "v1");
        if (ts == null || v1 == null) {
            return false;
        }
        // MP normaliza a minúsculas los id alfanuméricos; los de pago son numéricos (sin efecto)
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

    /** Extrae el valor de una clave (ts / v1) del header x-signature, separado por comas. */
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
