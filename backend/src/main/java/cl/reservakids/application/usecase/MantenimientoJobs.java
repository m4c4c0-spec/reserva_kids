package cl.reservakids.application.usecase;

import cl.reservakids.domain.model.Cliente;
import cl.reservakids.domain.model.EstadoBloque;
import cl.reservakids.domain.model.EstadoReserva;
import cl.reservakids.domain.model.Reserva;
import cl.reservakids.domain.model.Tenant;
import cl.reservakids.domain.repository.BloqueDisponibleRepository;
import cl.reservakids.domain.repository.ClienteRepository;
import cl.reservakids.domain.repository.PagoRepository;
import cl.reservakids.domain.repository.PasswordResetTokenRepository;
import cl.reservakids.domain.repository.RefreshTokenClienteRepository;
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
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Jobs de mantenimiento y cumplimiento (Ley 21.719, limpieza, offboarding).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MantenimientoJobs {

    private final ReservaRepository reservaRepository;
    private final BloqueDisponibleRepository bloqueRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenClienteRepository refreshTokenClienteRepository;
    private final ClienteRepository clienteRepository;
    private final TenantRepository tenantRepository;
    private final PagoRepository pagoRepository;
    private final ServicioRepository servicioRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final Clock clock;

    @Value("${app.clientes.retencion-meses}")
    private long retencionClienteMeses;

    @Value("${app.tenants.purga-dias-tras-cierre}")
    private long purgaDiasTrasCierre;

    /**
     * Mantenimiento diario (04:30): purga refresh tokens revocados/expirados (dueños y
     * clientes) y tokens de reset de contraseña usados/vencidos (falla 1.3) — ninguna
     * tabla crece sin límite.
     */
    @Scheduled(cron = "0 30 4 * * *")
    @Transactional
    public void purgarRefreshTokens() {
        OffsetDateTime ahora = OffsetDateTime.now(clock);
        int eliminados = refreshTokenRepository.purgarInvalidos(ahora);
        int clientes = refreshTokenClienteRepository.purgarInvalidos(ahora);
        int resets = passwordResetTokenRepository.purgarInvalidos(ahora);
        if (eliminados > 0 || clientes > 0 || resets > 0) {
            log.info("Mantenimiento: {} refresh tokens dueños, {} refresh tokens clientes y {} tokens de reset purgados",
                    eliminados, clientes, resets);
        }
    }

    /**
     * Falla #4 (revisión a 2 años, Ley 21.719 — minimización): clientes sin actividad por más
     * de la retención configurada se anonimizan (la fila se conserva: las reservas históricas
     * la referencian, pero deja de contener datos personales). Mensual, día 1 a las 05:00.
     * Un cliente con reservas activas todavía no es candidato — por eso depende del barrido
     * de {@link CicloReservaJobs#realizarConcluidas()}.
     */
    @Scheduled(cron = "0 0 5 1 * *")
    @Transactional
    public void anonimizarInactivos() {
        OffsetDateTime ahora = OffsetDateTime.now(clock);
        OffsetDateTime limite = ahora.minusMonths(retencionClienteMeses);
        int anonimizados = 0;

        for (Cliente cliente : clienteRepository.findByAnonimizadoEnIsNullAndUltimaActividadEnBefore(limite)) {
            if (reservaRepository.existsByClienteIdAndEstadoIn(cliente.getId(), EstadoReserva.ACTIVOS)) {
                continue; // todavía tiene reservas vigentes: su actividad no terminó
            }
            cliente.anonimizar(ahora);
            // Falla 3.2 (5 años): los comentarios de sus reservas también son datos personales
            reservaRepository.anonimizarComentariosDeCliente(
                    cliente.getId(), Reserva.COMENTARIOS_ANONIMIZADOS);
            anonimizados++;
        }
        if (anonimizados > 0) {
            log.info("Ley 21.719: {} clientes anonimizados por inactividad (> {} meses)",
                    anonimizados, retencionClienteMeses);
        }
    }

    /**
     * Falla #11 (revisión a 2 años): bloques DISPONIBLE con fecha pasada y sin ninguna reserva
     * que los referencie son solo ruido histórico — se eliminan los lunes a las 04:45.
     */
    @Scheduled(cron = "0 45 4 * * MON")
    @Transactional
    public void limpiarBloquesPasados() {
        int eliminados = bloqueRepository.eliminarPasadosSinReserva(LocalDate.now(clock), EstadoBloque.DISPONIBLE);
        if (eliminados > 0) {
            log.info("Mantenimiento: {} bloques pasados sin reservas eliminados", eliminados);
        }
    }

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
