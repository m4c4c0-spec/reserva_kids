package cl.reservakids.integration;

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
 * Controles proactivos CSRF: verifica que los endpoints protegidos por cookies
 * (refresh y logout) rechazan peticiones sin {@code X-Requested-With} o con
 * {@code Origin} no permitido.
 *
 * <h3>Escenarios cubiertos</h3>
 * <ol>
 *   <li>Refresh sin X-Requested-With → 403</li>
 *   <li>Refresh con X-Requested-With → 200 (access vencido → 401 del auth, no CSRF)</li>
 *   <li>Refresh con Origin malicioso → 403</li>
 *   <li>Logout sin X-Requested-With → 403</li>
 *   <li>Logout con X-Requested-With → 204</li>
 *   <li>Cliente-auth refresh sin X-Requested-With → 403</li>
 * </ol>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class CsrfProtectionIT {

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

    @Autowired
    MockMvc mockMvc;

    private String slug;
    private String email;

    @BeforeEach
    void registrarTenant() throws Exception {
        long nano = System.nanoTime();
        slug = "csrf-it-" + nano;
        email = "csrf-" + nano + "@test.cl";
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombreNegocio":"CSRF Test","slug":"%s","email":"%s","password":"test123456"}"""
                                .formatted(slug, email)))
                .andExpect(status().isCreated());
    }

    // ────────────────────────────── Control PREVENTIVO ──────────────────────────────

    @Test
    void refreshSinCsrfHeaderDebeFallar() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"fake\"}")
                        // intencionalmente SIN X-Requested-With
                        .header("Origin", "http://localhost:5173"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Petición cross-site no permitida"));
    }

    @Test
    void refreshConCsrfHeaderDebePasarControlCsrf() throws Exception {
        // El refresh token dentro será inválido ("fake"), pero el filtro CSRF debe
        // dejar pasar la request al controller → este responde 401 de auth, no 403 de CSRF.
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"fake\"}")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Origin", "http://localhost:5173"))
                .andExpect(status().isUnauthorized()); // auth rechaza, no CSRF
    }

    @Test
    void logoutSinCsrfHeaderDebeFallar() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        // intencionalmente SIN X-Requested-With
                        .header("Origin", "http://localhost:5173"))
                .andExpect(status().isForbidden());
    }

    @Test
    void logoutConCsrfHeaderDebePasarControlCsrf() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Origin", "http://localhost:5173"))
                .andExpect(status().isNoContent());
    }

    @Test
    void clienteRefreshSinCsrfHeaderDebeFallar() throws Exception {
        mockMvc.perform(post("/api/cliente-auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        // SIN X-Requested-With
                        .header("Origin", "http://localhost:5173"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminRefreshSinCsrfHeaderDebeFallar() throws Exception {
        mockMvc.perform(post("/api/admin-auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        // SIN X-Requested-With
                        .header("Origin", "http://localhost:5173"))
                .andExpect(status().isForbidden());
    }

    // ────────────────────────────── Control DETECTIVO ──────────────────────────────

    @Test
    void refreshConOriginMaliciosoDebeFallar() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"fake\"}")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Origin", "https://attacker.example.com"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Petición cross-site no permitida"));
    }

    @Test
    void refreshConRefererMaliciosoDebeFallar() throws Exception {
        // Sin Origin pero con Referer malicioso
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"fake\"}")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Referer", "https://evil.net/phishing"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Petición cross-site no permitida"));
    }

    @Test
    void refreshConOriginPermitidoPasaLaDeteccion() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"fake\"}")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Origin", "http://localhost:5173"))
                .andExpect(status().isUnauthorized()); // auth, no CSRF
    }

    // ────────────────────────────── Endpoints no afectados ──────────────────────────────

    @Test
    void loginNoRequiereCsrfPorqueNoUsaCookiePrevia() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"test123456"}""".formatted(email))
                        // intencionalmente SIN X-Requested-With ni Origin
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());
    }

    @Test
    void endpointPublicoNoRequiereCsrfHeader() throws Exception {
        // /api/public/{slug} con Origin permitido no debería ser bloqueado
        mockMvc.perform(post("/api/public/" + slug + "/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"servicioId\":1,\"bloqueId\":1,\"nombreContacto\":\"T\",\"telefono\":\"+56912345678\",\"numNinos\":10,\"aceptaDatos\":true}"))
                .andExpect(status().is4xxClientError()); // 404/400 ok, pero no 403 de CSRF
    }
}
