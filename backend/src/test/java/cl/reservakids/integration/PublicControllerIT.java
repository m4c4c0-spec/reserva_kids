package cl.reservakids.integration;

import cl.reservakids.application.dto.HorarioAtencionDtos.FranjaRequest;
import cl.reservakids.application.dto.HorarioAtencionDtos.HorarioRequest;
import cl.reservakids.application.usecase.HorarioAtencionService;
import cl.reservakids.domain.model.Servicio;
import cl.reservakids.domain.model.Tenant;
import cl.reservakids.domain.repository.ServicioRepository;
import cl.reservakids.domain.repository.TenantRepository;
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

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Sprint 3 (B3): tests de integración de los endpoints públicos ({@code /api/public/{slug}}).
 * Verifica el catálogo de servicios, las horas disponibles y el 404 para un slug inexistente —
 * los caminos que un bot o un cliente potencial pega primero, sin autenticación.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class PublicControllerIT {

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
    @Autowired ServicioRepository servicioRepository;
    @Autowired HorarioAtencionService horarioAtencionService;

    private String slug;

    @BeforeEach
    void setup() {
        slug = "public-it-" + System.nanoTime();
        Tenant tenant = new Tenant();
        tenant.setNombre("Fiestas Test");
        tenant.setSlug(slug);
        tenant.setEstado(Tenant.ESTADO_ACTIVO);
        Long tenantId = tenantRepository.save(tenant).getId();

        Servicio s1 = new Servicio();
        s1.setTenantId(tenantId);
        s1.setNombre("Recreación 1h");
        s1.setPrecioClp(15_000);
        s1.setDuracionMin(60);
        s1.setActivo(true);
        servicioRepository.save(s1);

        Servicio s2 = new Servicio();
        s2.setTenantId(tenantId);
        s2.setNombre("Maquillaje");
        s2.setPrecioClp(5_000);
        s2.setDuracionMin(30);
        s2.setActivo(true);
        servicioRepository.save(s2);

        // Lunes 10:00–12:00, intervalo 30 min — base para el test de horas libres.
        horarioAtencionService.guardar(tenantId,
                new HorarioRequest(30, List.of(new FranjaRequest(1, LocalTime.of(10, 0), LocalTime.of(12, 0)))));
    }

    @Test
    void catalogoDevuelveNombreYServiciosActivos() throws Exception {
        mockMvc.perform(get("/api/public/{slug}", slug).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value(slug))
                .andExpect(jsonPath("$.nombre").value("Fiestas Test"))
                .andExpect(jsonPath("$.servicios[?(@.nombre == 'Recreación 1h')]").exists())
                .andExpect(jsonPath("$.servicios[?(@.nombre == 'Maquillaje')]").exists());
    }

    @Test
    void slugInexistenteDevuelve404() throws Exception {
        mockMvc.perform(get("/api/public/{slug}", "no-existe-negocio-" + System.nanoTime()))
                .andExpect(status().isNotFound());
    }

    @Test
    void horasLibresDevuelveSlotsDentroDelHorario() throws Exception {
        LocalDate lunes = LocalDate.now(java.time.ZoneId.of("America/Santiago"))
                .with(java.time.DayOfWeek.MONDAY);
        if (!lunes.isAfter(LocalDate.now(java.time.ZoneId.of("America/Santiago")))) {
            lunes = lunes.plusWeeks(1);
        }

        mockMvc.perform(get("/api/public/{slug}/horas", slug)
                        .param("fecha", lunes.toString())
                        .param("duracion", "60"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("10:00"))
                .andExpect(jsonPath("$[1]").value("10:30"))
                .andExpect(jsonPath("$[2]").value("11:00"));
    }
}
