package cl.reservakids;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // jobs: expiración de solicitudes (RF-05) y purga de refresh tokens
public class ReservaKidsApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReservaKidsApplication.class, args);
    }
}
