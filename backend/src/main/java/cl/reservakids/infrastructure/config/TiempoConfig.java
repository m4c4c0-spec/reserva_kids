package cl.reservakids.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * El servidor (Docker/VPS) corre en UTC, pero el negocio opera en hora chilena:
 * sin esto, "hoy" difiere hasta 4 h respecto del usuario (bloques de mañana
 * rechazados como "pasado", disponibilidad con días corridos cerca de medianoche).
 */
@Configuration
public class TiempoConfig {

    @Bean
    public Clock clock(@Value("${app.zona-horaria}") String zona) {
        return Clock.system(ZoneId.of(zona));
    }
}
