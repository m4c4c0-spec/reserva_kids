package cl.reservakids.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Habilita los jobs {@code @Scheduled} (expiración de solicitudes RF-05, purga de tokens,
 * mantenimiento, recordatorios) en todos los perfiles MENOS "test".
 * <p>
 * En los IT con Testcontainers los schedulers competirían por conexiones del pool Hikari
 * con los tests de concurrencia (que lanzan ~10 hilos), agotándolo y provocando timeouts /
 * caídas del contenedor. Además dispararían trabajos no determinísticos durante los tests.
 * Los jobs se prueban de forma aislada invocándolos directamente en tests unitarios.
 */
@Configuration
@EnableScheduling
@Profile("!test")
public class SchedulingConfig {
}
