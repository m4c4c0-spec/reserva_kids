package cl.reservakids.integration;

import cl.reservakids.domain.model.Tenant;
import cl.reservakids.domain.repository.TenantRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * F2: tests de integración de la consola de admin ({@code /api/admin/**}) contra Postgres real.
 * Valida lo que los mocks no pueden: el query agregado {@code listarParaAdmin} (LEFT JOIN + GROUP
 * BY) parsea y corre, el routing por rol (sin token → 401) y el cambio de estado de un negocio.
 * El primer admin se crea por bootstrap desde propiedades (espejo de las env de prod).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class AdminControllerIT {

    static final String ADMIN_EMAIL = "ops@reservakids.cl";
    static final String ADMIN_PASS = "admin-super-seguro-123";

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
        registry.add("app.admin.bootstrap-email", () -> ADMIN_EMAIL);
        registry.add("app.admin.bootstrap-password", () -> ADMIN_PASS);
    }

    @Autowired MockMvc mockMvc;
    @Autowired TenantRepository tenantRepository;
    @Autowired ObjectMapper objectMapper;

    private String accessToken() throws Exception {
        String body = """
                {"email":"%s","password":"%s"}""".formatted(ADMIN_EMAIL, ADMIN_PASS);
        String json = mockMvc.perform(post("/api/admin-auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(json);
        return node.get("accessToken").asText();
    }

    private Long crearTenant(String estado) {
        Tenant t = new Tenant();
        t.setNombre("Negocio " + System.nanoTime());
        t.setSlug("admin-it-" + System.nanoTime());
        t.setEstado(estado);
        return tenantRepository.save(t).getId();
    }

    @Test
    void sinTokenLaConsolaSeDeniega() throws Exception {
        // Stateless sin entry point → Spring Security responde 403 a la ruta protegida.
        mockMvc.perform(get("/api/admin/negocios"))
                .andExpect(status().isForbidden());
    }

    @Test
    void listarNegociosCorreElQueryAgregadoYDevuelveConteos() throws Exception {
        Long id = crearTenant(Tenant.ESTADO_ACTIVO);
        mockMvc.perform(get("/api/admin/negocios").header("Authorization", "Bearer " + accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")]").exists())
                .andExpect(jsonPath("$[?(@.id == " + id + ")].reservas").value(org.hamcrest.Matchers.hasItem(0)));
    }

    @Test
    void suspenderCambiaElEstadoDelNegocio() throws Exception {
        Long id = crearTenant(Tenant.ESTADO_ACTIVO);
        String token = accessToken();

        mockMvc.perform(post("/api/admin/negocios/{id}/suspender", id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value(Tenant.ESTADO_SUSPENDIDO));

        mockMvc.perform(post("/api/admin/negocios/{id}/reactivar", id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value(Tenant.ESTADO_ACTIVO));
    }

    @Test
    void metricasDevuelveKpisDePlataforma() throws Exception {
        crearTenant(Tenant.ESTADO_ACTIVO);
        mockMvc.perform(get("/api/admin/metricas").header("Authorization", "Bearer " + accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.negociosTotal").isNumber())
                .andExpect(jsonPath("$.negociosActivos").isNumber())
                .andExpect(jsonPath("$.reservasTotal").isNumber())
                .andExpect(jsonPath("$.apoderados").isNumber())
                .andExpect(jsonPath("$.recaudadoSenasClp").isNumber());
    }

    @Test
    void suspenderQuedaRegistradoEnLaBitacora() throws Exception {
        Long id = crearTenant(Tenant.ESTADO_ACTIVO);
        String token = accessToken();
        mockMvc.perform(post("/api/admin/negocios/{id}/suspender", id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/auditoria").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].accion").value("SUSPENDER_NEGOCIO"))
                .andExpect(jsonPath("$[0].adminEmail").value(ADMIN_EMAIL));
    }

    @Test
    void altaDeOtroAdminYListado() throws Exception {
        String token = accessToken();
        String nuevo = "nuevo-" + System.nanoTime() + "@reservakids.cl";
        mockMvc.perform(post("/api/admin/administradores")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Nuevo\",\"email\":\"" + nuevo + "\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(nuevo));
        mockMvc.perform(get("/api/admin/administradores").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email == '" + nuevo + "')]").exists());
    }

    @Test
    void filtraPorEstado() throws Exception {
        Long suspendido = crearTenant(Tenant.ESTADO_SUSPENDIDO);
        mockMvc.perform(get("/api/admin/negocios").param("estado", "SUSPENDIDO")
                        .header("Authorization", "Bearer " + accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + suspendido + ")]").exists())
                .andExpect(jsonPath("$[?(@.estado != 'SUSPENDIDO')]").doesNotExist());
    }
}
