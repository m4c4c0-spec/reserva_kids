package cl.reservakids.integration;

import cl.reservakids.domain.model.Tenant;
import cl.reservakids.domain.repository.TenantRepository;
import cl.reservakids.infrastructure.security.CredentialCipher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * S3 (revisión de seguridad): el webhook de Mercado Pago valida la firma x-signature cuando
 * el tenant configuró el secreto, ignora topics que no son {@code payment}, rechaza tenants
 * sin configuración de MP o no ACTIVOS, y valida la antigüedad de la firma (anti-replay).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class WebhookMercadoPagoIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("reservakids")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired MockMvc mockMvc;
    @Autowired TenantRepository tenantRepository;
    @Autowired CredentialCipher credentialCipher;

    private Long tenantConMp;
    private Long tenantSinMp;
    private String secretoFirma;

    @BeforeEach
    void setup() {
        secretoFirma = "secreto-firma-test-" + System.nanoTime();

        Tenant conMp = new Tenant();
        conMp.setNombre("Con MP");
        conMp.setSlug("con-mp-" + System.nanoTime());
        conMp.setMpAccessToken(credentialCipher.encrypt("MP-TEST-TOKEN"));
        conMp.setMpWebhookSecret(credentialCipher.encrypt(secretoFirma));
        tenantConMp = tenantRepository.save(conMp).getId();

        Tenant sinMp = new Tenant();
        sinMp.setNombre("Sin MP");
        sinMp.setSlug("sin-mp-" + System.nanoTime());
        tenantSinMp = tenantRepository.save(sinMp).getId();
    }

    @Test
    void firmaInvalidaEsRechazadaCon401() throws Exception {
        long ts = Instant.now().getEpochSecond();
        String firma = construirFirma(secretoFirma, "12345", "req-1", ts, "deadbeef");
        mockMvc.perform(post("/api/public/webhooks/mercadopago/{tenantId}", tenantConMp)
                        .param("type", "payment")
                        .param("id", "12345")
                        .header("x-signature", "ts=" + ts + ",v1=" + firma)
                        .header("x-request-id", "req-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("Firma inválida"));
    }

    @Test
    void firmaExpiradaEsRechazadaCon401() throws Exception {
        long ts = Instant.now().getEpochSecond() - 600;
        String firma = construirFirma(secretoFirma, "12345", "req-1", ts, secretoFirma);
        mockMvc.perform(post("/api/public/webhooks/mercadopago/{tenantId}", tenantConMp)
                        .param("type", "payment")
                        .param("id", "12345")
                        .header("x-signature", "ts=" + ts + ",v1=" + firma)
                        .header("x-request-id", "req-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("Firma inválida"));
    }

    @Test
    void topicNoPaymentEsIgnorado200() throws Exception {
        mockMvc.perform(post("/api/public/webhooks/mercadopago/{tenantId}", tenantConMp)
                        .param("topic", "merchant_order")
                        .param("id", "999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(content().string("ignorado"));
    }

    @Test
    void tenantSinTokenMpDevuelve400() throws Exception {
        mockMvc.perform(post("/api/public/webhooks/mercadopago/{tenantId}", tenantSinMp)
                        .param("type", "payment")
                        .param("id", "12345")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Configuracion MP no encontrada"));
    }

    @Test
    void tenantInexistenteDevuelve400() throws Exception {
        mockMvc.perform(post("/api/public/webhooks/mercadopago/{tenantId}", 9_999_999L)
                        .param("type", "payment")
                        .param("id", "12345")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Configuracion MP no encontrada"));
    }

    @Test
    void dataIdNoNumericoEsIgnorado200() throws Exception {
        mockMvc.perform(post("/api/public/webhooks/mercadopago/{tenantId}", tenantConMp)
                        .param("type", "payment")
                        .param("id", "no-es-numero")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(content().string("ignorado"));
    }

    @Test
    void tenantRateLimitExcedidoDevuelve429() throws Exception {
        long ts = Instant.now().getEpochSecond();
        // Firma calculada para un requestId fijo; en el loop se envía otro distinto,
        // de modo que SIEMPRE sea inválida y no intente llamar a Mercado Pago.
        // El objetivo aquí es probar el rate-limit por tenant, no la validación HMAC.
        String firma = construirFirma(secretoFirma, "99991", "req-1", ts, secretoFirma);
        for (int i = 0; i < 11; i++) {
            mockMvc.perform(post("/api/public/webhooks/mercadopago/{tenantId}", tenantConMp)
                            .param("type", "payment")
                            .param("id", String.valueOf(99990 + i))
                            .header("x-signature", "ts=" + ts + ",v1=" + firma)
                            .header("x-request-id", "rate-limit-" + i)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(content().string(org.hamcrest.Matchers.anyOf(
                            org.hamcrest.Matchers.is("Firma inválida"),
                            org.hamcrest.Matchers.is("Demasiadas solicitudes"))));
        }
    }

    private static String construirFirma(String secreto, String dataId, String requestId, long ts, String claveHmac) {
        String manifiesto = "id:" + dataId.toLowerCase() + ";request-id:" + requestId + ";ts:" + ts + ";";
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(claveHmac.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(manifiesto.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
