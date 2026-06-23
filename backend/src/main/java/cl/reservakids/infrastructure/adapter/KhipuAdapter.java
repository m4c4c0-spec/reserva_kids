package cl.reservakids.infrastructure.adapter;

import cl.reservakids.domain.exception.PasarelaPagoException;
import cl.reservakids.domain.model.Reserva;
import cl.reservakids.domain.model.Tenant;
import cl.reservakids.domain.repository.PasarelaPagoPort;
import cl.reservakids.infrastructure.security.CredentialCipher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * V25: integración con Khipu (API v3 de Pagos Instantáneos). Misma idea que
 * {@link MercadoPagoAdapter}: crea una intención de pago y devuelve la URL a la que se
 * redirige al pagador (Khipu payment_url). La confirmación llega por webhook
 * ({@link cl.reservakids.infrastructure.web.KhipuWebhookController}).
 *
 * <p>Auth de Khipu v3: una sola credencial por cuenta de cobro, la API key, que viaja en la
 * cabecera {@code x-api-key} tanto al crear como al consultar el pago, y que además es el
 * secreto con el que se firma el webhook. Se guarda cifrada en reposo (S1).
 */
@Component
@Slf4j
public class KhipuAdapter implements PasarelaPagoPort {

    private final CredentialCipher credentialCipher;
    private final RestClient restClient;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Value("${app.api.url:http://localhost:8080}")
    private String apiUrl;

    public KhipuAdapter(CredentialCipher credentialCipher,
                        @Value("${app.khipu.api-base:https://api.khipu.com}") String apiBase) {
        this.credentialCipher = credentialCipher;
        this.restClient = RestClient.builder().baseUrl(apiBase).build();
    }

    @Override
    public PreferenciaPagoResponse crearPreferenciaDePago(Reserva reserva, Tenant tenant) {
        if (tenant.getKhipuApiKey() == null || tenant.getKhipuApiKey().isBlank()) {
            log.warn("Tenant {} no tiene API key de Khipu configurada", tenant.getSlug());
            return null;
        }
        try {
            String apiKey = credentialCipher.decrypt(tenant.getKhipuApiKey());

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("amount", reserva.getSeniaClp());
            body.put("currency", "CLP");
            body.put("subject", "Seña de Reserva - " + tenant.getNombre());
            body.put("transaction_id", String.valueOf(reserva.getId()));
            body.put("return_url", frontendUrl + "/" + tenant.getSlug() + "?pago=exito");
            body.put("cancel_url", frontendUrl + "/" + tenant.getSlug() + "?pago=fallo");
            body.put("notify_url", apiUrl + "/api/public/webhooks/khipu/" + tenant.getId());
            body.put("notify_api_version", "3.0");
            body.put("expires_date", DateTimeFormatter.ISO_OFFSET_DATE_TIME
                    .format(OffsetDateTime.now().plusHours(24)));

            KhipuCreateResponse resp = restClient.post()
                    .uri("/v3/payments")
                    .header("x-api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(KhipuCreateResponse.class);

            return new PreferenciaPagoResponse(resp.payment_id(), resp.payment_url());
        } catch (Exception e) {
            log.error("Error al crear pago en Khipu para tenant {}: {}", tenant.getSlug(), e.getMessage());
            throw new PasarelaPagoException("Error al comunicarse con Khipu", e);
        }
    }

    /** Consulta canónica del estado de un pago (usada por el webhook para no fiarse del body). */
    public Optional<KhipuPago> obtenerPago(String paymentId, Tenant tenant) {
        if (tenant.getKhipuApiKey() == null || tenant.getKhipuApiKey().isBlank()) {
            return Optional.empty();
        }
        try {
            String apiKey = credentialCipher.decrypt(tenant.getKhipuApiKey());
            KhipuPago pago = restClient.get()
                    .uri("/v3/payments/{id}", paymentId)
                    .header("x-api-key", apiKey)
                    .retrieve()
                    .body(KhipuPago.class);
            return Optional.ofNullable(pago);
        } catch (Exception e) {
            log.warn("Khipu GET /payments/{}/{} falló: {}", paymentId, tenant.getSlug(), e.getMessage());
            return Optional.empty();
        }
    }

    public record KhipuCreateResponse(String payment_id, String payment_url) {}

    public record KhipuPago(String payment_id, String status, Number amount, String transaction_id) {}
}