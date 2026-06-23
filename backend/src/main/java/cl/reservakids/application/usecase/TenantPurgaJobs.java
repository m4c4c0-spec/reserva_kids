package cl.reservakids.application.usecase;

import cl.reservakids.domain.model.Tenant;
import cl.reservakids.domain.repository.BloqueDisponibleRepository;
import cl.reservakids.domain.repository.ClienteRepository;
import cl.reservakids.domain.repository.PagoRepository;
import cl.reservakids.domain.repository.PasswordResetTokenRepository;
import cl.reservakids.domain.repository.RefreshTokenRepository;
import cl.reservakids.domain.repository.ReservaRepository;
import cl.reservakids.domain.repository.ServicioRepository;
import cl.reservakids.domain.repository.TenantRepository;
import cl.reservakids.domain.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;

/**
 * Purga física diferida de tenants cerrados (offboarding, Ley 21.719 — supresión real).
 * Las dependencias son numerosas pero cohesivas: una sola cascada de borrado gobernada
 * por las FK del tenant.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantPurgaJobs {

    private final ReservaRepository reservaRepository;
    private final BloqueDisponibleRepository bloqueRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ClienteRepository clienteRepository;
    private final TenantRepository tenantRepository;
    private final PagoRepository pagoRepository;
    private final ServicioRepository servicioRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final Clock clock;

    @Value("${app.tenants.purga-dias-tras-cierre}")
    private long purgaDiasTrasCierre;

    /**
     * Falla 3.3 (revisión a 5 años): purga física de tenants CERRADOS cuya ventana de gracia
     * (TENANT_PURGA_DIAS, 90) ya venció — el dueño tuvo su export y su plazo de arrepentimiento
     * (reapertura por runbook, OPERACION.md §7). Borra TODO el rastro del negocio, incluidos
     * los datos de sus clientes apoderados: supresión real Ley 21.719, que además elimina el
     * residuo de datos personales en comentarios (falla 3.2) para estos tenants. Reduce BD y
     * backups (falla 4.2) e idempotente: correr dos veces no purga dos veces (falla 4.4).
     * Mensual, día 2 a las 05:30 (después de la anonimización del día 1).
     */
    @Scheduled(cron = "0 30 5 2 * *")
    @Transactional
    public void purgarTenantsCerrados() {
        OffsetDateTime limite = OffsetDateTime.now(clock).minusDays(purgaDiasTrasCierre);
        for (Tenant tenant : tenantRepository.findByEstadoAndCerradoEnBefore(Tenant.ESTADO_CERRADO, limite)) {
            Long id = tenant.getId();
            // Orden gobernado por las FK: pago → reserva → bloque/cliente/servicio → tokens → usuario
            pagoRepository.eliminarDeTenant(id);
            reservaRepository.eliminarDeTenant(id);
            bloqueRepository.eliminarDeTenant(id);
            clienteRepository.eliminarDeTenant(id);
            servicioRepository.eliminarDeTenant(id);
            refreshTokenRepository.eliminarDeTenant(id);
            passwordResetTokenRepository.eliminarDeTenant(id);
            usuarioRepository.eliminarDeTenant(id);
            tenantRepository.delete(tenant);
            log.info("Offboarding: tenant '{}' (#{}) purgado físicamente ({} días tras su cierre)",
                    tenant.getSlug(), id, purgaDiasTrasCierre);
        }
    }
}
