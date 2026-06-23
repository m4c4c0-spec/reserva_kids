package cl.reservakids.application.usecase;

import cl.reservakids.domain.model.Administrador;
import cl.reservakids.domain.repository.AdministradorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Crea el primer administrador al arranque desde variables de entorno, evitando el problema
 * del huevo-y-la-gallina sin hornear un hash en el repo (deuda de seguridad + historial git).
 *
 *   ADMIN_BOOTSTRAP_EMAIL    + ADMIN_BOOTSTRAP_PASSWORD
 *
 * Idempotente: si el admin con ese email ya existe, no hace nada. Fail-safe: si alguna var
 * está vacía, NO crea nada y NO rompe el arranque (igual que JWT_SECRET vacío degrada en dev).
 * En demo/prod se setean una vez y luego pueden quitarse del entorno.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private final AdministradorRepository administradorRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.bootstrap-email:}")
    private String bootstrapEmail;

    @Value("${app.admin.bootstrap-password:}")
    private String bootstrapPassword;

    @Value("${app.admin.bootstrap-nombre:Administrador}")
    private String bootstrapNombre;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (bootstrapEmail == null || bootstrapEmail.isBlank()
                || bootstrapPassword == null || bootstrapPassword.isBlank()) {
            return; // sin credenciales de bootstrap → no se crea ningún admin
        }
        String email = bootstrapEmail.trim().toLowerCase(Locale.ROOT);
        if (administradorRepository.existsByEmail(email)) {
            return; // ya existe → idempotente
        }
        Administrador admin = new Administrador();
        admin.setEmail(email);
        admin.setNombre(bootstrapNombre == null || bootstrapNombre.isBlank() ? "Administrador" : bootstrapNombre.trim());
        admin.setPasswordHash(passwordEncoder.encode(bootstrapPassword));
        administradorRepository.save(admin);
        log.info("AdminBootstrap: administrador de plataforma creado para {}", email);
    }
}
