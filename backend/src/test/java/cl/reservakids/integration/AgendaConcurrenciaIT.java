package cl.reservakids.integration;

import cl.reservakids.application.dto.AgendaDtos.AgendarCitaRequest;
import cl.reservakids.application.dto.HorarioAtencionDtos.FranjaRequest;
import cl.reservakids.application.dto.HorarioAtencionDtos.HorarioRequest;
import cl.reservakids.application.usecase.AgendaService;
import cl.reservakids.application.usecase.HorarioAtencionService;
import cl.reservakids.domain.exception.ConflictoBloqueException;
import cl.reservakids.domain.model.CuentaCliente;
import cl.reservakids.domain.model.Servicio;
import cl.reservakids.domain.model.Tenant;
import cl.reservakids.domain.repository.CuentaClienteRepository;
import cl.reservakids.domain.repository.PasarelaPagoPort;
import cl.reservakids.domain.repository.ServicioRepository;
import cl.reservakids.domain.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * V15: el advisory lock por (tenant, fecha) + re-verificación de {@code horasLibres} evita
 * que dos clientes concurrentes agenden la misma hora. Dos hilos intentan agendar la misma
 * franja; solo uno gana y el otro recibe {@link ConflictoBloqueException}.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class AgendaConcurrenciaIT {

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

    @Autowired AgendaService agendaService;
    @Autowired HorarioAtencionService horarioAtencionService;
    @Autowired TenantRepository tenantRepository;
    @Autowired ServicioRepository servicioRepository;
    @Autowired CuentaClienteRepository cuentaClienteRepository;
    @Autowired Clock clock;

    @MockitoBean
    PasarelaPagoPort pasarelaPagoPort;

    private String slug;
    private Long cuentaId;
    private AgendarCitaRequest req;

    @BeforeEach
    void setup() {
        when(pasarelaPagoPort.crearPreferenciaDePago(any(), any()))
                .thenReturn(new PasarelaPagoPort.PreferenciaPagoResponse("pref-test", "http://init.test"));

        Tenant tenant = new Tenant();
        tenant.setNombre("Fiestas Test");
        tenant.setSlug("fiestas-test-" + System.nanoTime());
        tenant = tenantRepository.save(tenant);
        slug = tenant.getSlug();

        // Horario solo para el día de la cita (10:00–18:00, intervalo 30).
        LocalDate fecha = LocalDate.now(clock).plusDays(8);
        int diaSemana = fecha.getDayOfWeek().getValue();
        horarioAtencionService.guardar(tenant.getId(), new HorarioRequest(30,
                List.of(new FranjaRequest(diaSemana, LocalTime.of(10, 0), LocalTime.of(18, 0)))));

        Servicio servicio = new Servicio();
        servicio.setTenantId(tenant.getId());
        servicio.setNombre("Cumple básico");
        servicio.setPrecioClp(50000);
        servicio.setDuracionMin(60);
        servicio.setActivo(true);
        servicio = servicioRepository.save(servicio);

        CuentaCliente cuenta = new CuentaCliente();
        cuenta.setEmail("cliente-" + System.nanoTime() + "@test.cl");
        cuenta.setNombre("Ana");
        cuenta.setTelefono("56911111111");
        cuenta.setPasswordHash("hash");
        cuenta = cuentaClienteRepository.save(cuenta);
        cuentaId = cuenta.getId();

        req = new AgendarCitaRequest(List.of(servicio.getId()), fecha, LocalTime.of(10, 0));
    }

    @Test
    void soloUnaCitaGanaLaMismaHora() throws InterruptedException {
        int hilos = 6;
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch listos = new CountDownLatch(hilos);
        CountDownLatch arranque = new CountDownLatch(1);
        AtomicInteger exitos = new AtomicInteger();
        AtomicInteger conflictos = new AtomicInteger();

        for (int i = 0; i < hilos; i++) {
            pool.submit(() -> {
                listos.countDown();
                try {
                    arranque.await();
                    agendaService.agendar(cuentaId, slug, req);
                    exitos.incrementAndGet();
                } catch (ConflictoBloqueException e) {
                    conflictos.incrementAndGet();
                } catch (Exception e) {
                    // cualquier otro fallo no esperado: lo cuenta como no-éxito
                } finally {
                    // el latch final lo gestiona el await de abajo
                }
                return null;
            });
        }
        listos.await();
        arranque.countDown(); // liberar a todos a la vez para maximizar la contención

        // esperar a que terminen
        pool.shutdown();
        while (!pool.isTerminated()) {
            pool.awaitTermination(1, java.util.concurrent.TimeUnit.SECONDS);
        }

        assertThat(exitos.get()).isEqualTo(1);
        assertThat(conflictos.get()).isEqualTo(hilos - 1);
    }
}
