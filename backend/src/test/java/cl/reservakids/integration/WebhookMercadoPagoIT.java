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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * S3 (revisión de seguridad): el webhook de Mercado Pago valida la firma x-signature cuando
 * el tenant configuró el secreto, ignora topics que no son {@code payment} y rechaza tenants
 * sin configuración de MP. Estos caminos no tocan la API de MP (no la mockeamos): validan el
 * parseo, el cifrado de credenciales en reposo y la verificación HMAC contra una BD real.
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

    @BeforeEach
    void setup() {
        Tenant conMp = new Tenant();
        conMp.setNombre("Con MP");
        conMp.setSlug("con-mp-" + System.nanoTime());
        conMp.setMpAccessToken(credentialCipher.encrypt("MP-TEST-TOKEN"));
        conMp.setMpWebhookSecret(credentialCipher.encrypt("secreto-firma-test"));
        tenantConMp = tenantRepository.save(conMp).getId();

        Tenant sinMp = new Tenant();
        sinMp.setNombre("Sin MP");
        sinMp.setSlug("sin-mp-" + System.nanoTime());
        tenantSinMp = tenantRepository.save(sinMp).getId();
    }

    @Test
    void firmaInvalidaEsRechazadaCon401() throws Exception {
        mockMvc.perform(post("/api/public/webhooks/mercadopago/{tenantId}", tenantConMp)
                        .param("type", "payment")
                        .param("id", "12345")
                        .header("x-signature", "ts=1718000000,v1=deadbeef")
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
}
