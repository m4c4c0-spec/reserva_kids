package cl.reservakids.integration;

import cl.reservakids.application.dto.HorarioAtencionDtos.FranjaRequest;
import cl.reservakids.application.dto.HorarioAtencionDtos.HorarioRequest;
import cl.reservakids.application.usecase.DisponibilidadService;
import cl.reservakids.application.usecase.HorarioAtencionService;
import cl.reservakids.domain.model.Tenant;
import cl.reservakids.domain.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V13: el horario de atención gobierna las horas agendables. Tras guardar franjas para un día
 * y un intervalo, {@link DisponibilidadService#horasLibres} devuelve exactamente los slots que
 * caben en la franja; un día sin horario no ofrece nada.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class HorarioAtencionIT {

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

    @Autowired HorarioAtencionService horarioAtencionService;
    @Autowired DisponibilidadService disponibilidadService;
    @Autowired TenantRepository tenantRepository;
    @Autowired Clock clock;

    private Long tenantId;
    private LocalDate lunes;
    private LocalDate martes;

    @BeforeEach
    void setup() {
        Tenant tenant = new Tenant();
        tenant.setNombre("Fiestas Test");
        tenant.setSlug("horario-test-" + System.nanoTime());
        tenantId = tenantRepository.save(tenant).getId();

        // Próximo lunes y próximo martes a partir de hoy (zona del negocio).
        LocalDate hoy = LocalDate.now(clock);
        lunes = hoy.with(java.time.DayOfWeek.MONDAY);
        if (!lunes.isAfter(hoy)) lunes = lunes.plusWeeks(1);
        martes = lunes.plusDays(1);

        // Solo lunes 10:00–12:00, intervalo 30 min.
        horarioAtencionService.guardar(tenantId, new HorarioRequest(30,
                List.of(new FranjaRequest(1, LocalTime.of(10, 0), LocalTime.of(12, 0)))));
    }

    @Test
    void horasLibresReflejanElHorarioGuardado() {
        List<LocalTime> libres = disponibilidadService.horasLibres(tenantId, lunes, 60);

        // 10:00 (10–11), 10:30 (10:30–11:30) y 11:00 (11–12) caben; 11:30 no (11:30–12:30 > 12:00).
        assertThat(libres).containsExactly(LocalTime.of(10, 0), LocalTime.of(10, 30), LocalTime.of(11, 0));
    }

    @Test
    void diaSinHorarioNoTieneHorasLibres() {
        assertThat(disponibilidadService.horasLibres(tenantId, martes, 60)).isEmpty();
    }

    @Test
    void duracionMayorQueLaFranjaNoDaOpciones() {
        assertThat(disponibilidadService.horasLibres(tenantId, lunes, 180)).isEmpty();
    }
}
