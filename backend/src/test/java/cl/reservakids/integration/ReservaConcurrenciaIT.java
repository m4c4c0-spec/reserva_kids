package cl.reservakids.integration;

import cl.reservakids.application.dto.ReservaDtos;
import cl.reservakids.application.usecase.ReservaService;
import cl.reservakids.domain.exception.ConflictoBloqueException;
import cl.reservakids.domain.model.BloqueDisponible;
import cl.reservakids.domain.model.EstadoBloque;
import cl.reservakids.domain.model.Servicio;
import cl.reservakids.domain.model.Tenant;
import cl.reservakids.domain.repository.BloqueDisponibleRepository;
import cl.reservakids.domain.repository.ReservaRepository;
import cl.reservakids.domain.repository.ServicioRepository;
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

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RNF-05: verifica que dos solicitudes simultáneas sobre el mismo bloque no generen
 * una doble reserva. El mecanismo es el UPDATE condicionado + índice único parcial.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class ReservaConcurrenciaIT {

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

    @Autowired ReservaService reservaService;
    @Autowired TenantRepository tenantRepository;
    @Autowired ServicioRepository servicioRepository;
    @Autowired BloqueDisponibleRepository bloqueRepository;
    @Autowired ReservaRepository reservaRepository;

    private String slug;
    private Long bloqueId;

    @BeforeEach
    void setup() {
        Tenant tenant = new Tenant();
        tenant.setNombre("Fiestas Test");
        tenant.setSlug("fiestas-test-" + System.nanoTime());
        tenant = tenantRepository.save(tenant);
        slug = tenant.getSlug();

        Servicio servicio = new Servicio();
        servicio.setTenantId(tenant.getId());
        servicio.setNombre("Cumple básico");
        servicio.setPrecioClp(100000);
        servicio.setActivo(true);
        servicioRepository.save(servicio);

        BloqueDisponible bloque = new BloqueDisponible();
        bloque.setTenantId(tenant.getId());
        bloque.setFecha(LocalDate.now().plusDays(7));
        bloque.setHoraInicio(LocalTime.of(15, 0));
        bloque.setHoraFin(LocalTime.of(18, 0));
        bloque.setEstado(EstadoBloque.DISPONIBLE);
        bloque = bloqueRepository.save(bloque);
        bloqueId = bloque.getId();
    }

    @Test
    void soloUnaSolicitudGanaElBloque() throws InterruptedException {
        int hilos = 10;
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch latch = new CountDownLatch(hilos);
        AtomicInteger exitos = new AtomicInteger();
        AtomicInteger conflictos = new AtomicInteger();

        ReservaDtos.SolicitudPublicaRequest req = new ReservaDtos.SolicitudPublicaRequest(
                servicioRepository.findAll().get(0).getId(),
                bloqueId,
                "Ana", "11.111.111-1", "56911111111", "ana@test.cl", 10, "Temuco", "", true);

        for (int i = 0; i < hilos; i++) {
            pool.submit(() -> {
                try {
                    reservaService.crearSolicitudPublica(slug, req);
                    exitos.incrementAndGet();
                } catch (ConflictoBloqueException e) {
                    conflictos.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        pool.shutdown();

        long reservasActivas = reservaRepository.findAll().stream()
                .filter(r -> !r.getEstado().name().equals("CANCELADA"))
                .count();

        assertThat(exitos.get()).isEqualTo(1);
        assertThat(conflictos.get()).isEqualTo(hilos - 1);
        assertThat(reservasActivas).isEqualTo(1);
    }
}
