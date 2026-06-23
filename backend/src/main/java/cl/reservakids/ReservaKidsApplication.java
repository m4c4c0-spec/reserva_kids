package cl.reservakids;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// @EnableScheduling vive en SchedulingConfig (@Profile("!test")): así los jobs no compiten
// por el pool de conexiones con los IT de concurrencia ni introducen no-determinismo en tests.
@SpringBootApplication
public class ReservaKidsApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReservaKidsApplication.class, args);
    }
}
