package cl.reservakids.integration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REVISION_ARQUITECTURA.md A1: test de integración mínimo que levanta el contexto de
 * Spring Boot contra una base PostgreSQL real gestionada por Testcontainers.
 * Valida que las migraciones Flyway corren sin errores y que la aplicación arranca.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class ContextoIT {

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

    @Test
    void contextoCargaYMigracionesCorren() {
        assertThat(postgres.isRunning()).isTrue();
    }
}
