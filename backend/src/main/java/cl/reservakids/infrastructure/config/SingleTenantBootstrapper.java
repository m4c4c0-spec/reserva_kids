package cl.reservakids.infrastructure.config;

import cl.reservakids.application.dto.AuthDtos.RegistroRequest;
import cl.reservakids.application.usecase.AuthService;
import cl.reservakids.domain.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Bootstrapper para entornos Single-Tenant (Licencia Exclusiva High-Ticket).
 * Si está activado mediante variables de entorno y la base de datos de Tenants
 * está vacía, aprovisiona automáticamente al negocio y su dueño.
 * Evita tener que ejecutar scripts SQL manuales al levantar un nuevo cliente.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SingleTenantBootstrapper implements ApplicationRunner {

    private final TenantRepository tenantRepository;
    private final AuthService authService;

    @Value("${app.single-tenant.enabled:false}")
    private boolean enabled;

    @Value("${app.single-tenant.slug:}")
    private String slug;

    @Value("${app.single-tenant.nombre:}")
    private String nombre;

    @Value("${app.single-tenant.admin-email:}")
    private String adminEmail;

    @Value("${app.single-tenant.admin-password:}")
    private String adminPassword;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!enabled) {
            log.debug("Modo Single-Tenant desactivado.");
            return;
        }

        if (tenantRepository.count() > 0) {
            log.info("Modo Single-Tenant activo: la base de datos ya contiene Tenants. Omitiendo aprovisionamiento.");
            return;
        }

        log.info("Modo Single-Tenant activo y BD vacía. Iniciando aprovisionamiento automático...");

        if (slug.isBlank() || nombre.isBlank() || adminEmail.isBlank() || adminPassword.isBlank()) {
            log.error("Faltan variables de entorno para aprovisionar el Single-Tenant. Revisa RESERVAKIDS_SINGLE_TENANT_*");
            return;
        }

        try {
            RegistroRequest req = new RegistroRequest(
                    nombre,
                    slug,
                    adminEmail,
                    adminPassword
            );
            authService.registrar(req);
            log.info("✅ Single-Tenant aprovisionado exitosamente: '{}' (slug: {}, admin: {})", nombre, slug, adminEmail);
        } catch (Exception e) {
            log.error("❌ Error al aprovisionar el Single-Tenant: {}", e.getMessage());
        }
    }
}
