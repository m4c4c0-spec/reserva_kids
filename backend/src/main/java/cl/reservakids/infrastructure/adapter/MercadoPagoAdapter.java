package cl.reservakids.infrastructure.adapter;

import cl.reservakids.domain.exception.PasarelaPagoException;
import cl.reservakids.domain.model.Reserva;
import cl.reservakids.domain.model.Tenant;
import cl.reservakids.domain.repository.PasarelaPagoPort;
import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.preference.PreferenceBackUrlsRequest;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceItemRequest;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.core.MPRequestOptions;
import com.mercadopago.resources.preference.Preference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class MercadoPagoAdapter implements PasarelaPagoPort {

    private final cl.reservakids.infrastructure.security.CredentialCipher credentialCipher;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    // URL base para recibir los webhooks, debe ser pública
    // Ejemplo: https://api.reservakids.cl/api/public/webhooks/mercadopago
    @Value("${app.api.url:http://localhost:8080}")
    private String apiUrl;

    @Override
    public PreferenciaPagoResponse crearPreferenciaDePago(Reserva reserva, Tenant tenant) {
        if (tenant.getMpAccessToken() == null || tenant.getMpAccessToken().isBlank()) {
            log.warn("Tenant {} no tiene token de Mercado Pago configurado", tenant.getSlug());
            return null;
        }

        try {
            PreferenceItemRequest itemRequest = PreferenceItemRequest.builder()
                    .id("SENA-" + reserva.getId())
                    .title("Seña de Reserva - " + tenant.getNombre())
                    .description("Seña para reserva en " + tenant.getNombre())
                    .categoryId("services")
                    .quantity(1)
                    .currencyId("CLP")
                    .unitPrice(new BigDecimal(reserva.getSeniaClp()))
                    .build();

            List<PreferenceItemRequest> items = new ArrayList<>();
            items.add(itemRequest);

            PreferenceBackUrlsRequest backUrls = PreferenceBackUrlsRequest.builder()
                    .success(frontendUrl + "/" + tenant.getSlug() + "?pago=exito")
                    .pending(frontendUrl + "/" + tenant.getSlug() + "?pago=pendiente")
                    .failure(frontendUrl + "/" + tenant.getSlug() + "?pago=fallo")
                    .build();

            String webhookUrl = apiUrl + "/api/public/webhooks/mercadopago/" + tenant.getId();

            PreferenceRequest preferenceRequest = PreferenceRequest.builder()
                    .items(items)
                    .backUrls(backUrls)
                    .autoReturn("approved")
                    .externalReference(String.valueOf(reserva.getId())) // Muy importante para relacionar el webhook
                    .notificationUrl(webhookUrl)
                    .build();

            // Usar MPRequestOptions para modelo multi-tenant.
            // S1: el token está cifrado en reposo — se descifra solo aquí, al usarlo.
            MPRequestOptions requestOptions = MPRequestOptions.builder()
                    .accessToken(credentialCipher.decrypt(tenant.getMpAccessToken()))
                    .build();

            PreferenceClient client = new PreferenceClient();
            Preference preference = client.create(preferenceRequest, requestOptions);

            return new PreferenciaPagoResponse(preference.getId(), preference.getInitPoint());

        } catch (Exception e) {
            log.error("Error al crear preferencia en Mercado Pago para tenant {}: {}", tenant.getSlug(), e.getMessage());
            throw new PasarelaPagoException("Error al comunicarse con Mercado Pago", e);
        }
    }
}
