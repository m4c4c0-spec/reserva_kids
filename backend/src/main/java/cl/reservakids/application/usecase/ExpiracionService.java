package cl.reservakids.application.usecase;

import cl.reservakids.domain.model.Cliente;
import cl.reservakids.domain.model.EstadoBloque;
import cl.reservakids.domain.model.EstadoReserva;
import cl.reservakids.domain.model.Reserva;
import cl.reservakids.domain.model.Tenant;
import cl.reservakids.domain.repository.BloqueDisponibleRepository;
import cl.reservakids.domain.repository.ClienteRepository;
import cl.reservakids.domain.repository.PagoRepository;
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
import java.util.List;

/**
 * Jobs de mantenimiento del ciclo de vida (corren en una sola instancia — ver riesgos 1 año):
 * <ul>
 *   <li>RF-05: una solicitud PENDIENTE retiene su bloque EN_ESPERA por 48 h; vencida se cancela.</li>
 *   <li>Falla #1 (2 años): una COTIZADA sin respuesta libera su bloque tras N días.</li>
 *   <li>Falla #2 (2 años): CONFIRMADA con fecha ya pasada se marca REALIZADA (sin esto los
 *       clientes quedan "con reservas activas" para siempre y la anonimización nunca corre).</li>
 *   <li>Falla #4 (2 años, Ley 21.719): clientes inactivos se anonimizan al vencer la retención.</li>
 *   <li>Falla #11 (2 años): bloques DISPONIBLE pasados sin reservas se eliminan (ruido histórico).</li>
 *   <li>Falla #1 (1 año): purga de refresh tokens revocados/expirados.</li>
 *   <li>Falla 3.3 (5 años): tenants CERRADOS se purgan físicamente tras la ventana de gracia.</li>
 * </ul>
 * Todas las comparaciones con "hoy" usan el {@link Clock} del negocio (APP_TIMEZONE).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExpiracionService {

    private final ReservaRepository reservaRepository;
    private final BloqueDisponibleRepository bloqueRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ClienteRepository clienteRepository;
    private final TenantRepository tenantRepository;
    private final PagoRepository pagoRepository;
    private final ServicioRepository servicioRepository;
    private final UsuarioRepository usuarioRepository;
    private final Clock clock;

    @Value("${app.reservas.expiracion-horas}")
    private long expiracionHoras;

    @Value("${app.reservas.cotizacion-expiracion-dias}")
    private long cotizacionExpiracionDias;

    @Value("${app.clientes.retencion-meses}")
    private long retencionClienteMeses;

    @Value("${app.tenants.purga-dias-tras-cierre}")
    private long purgaDiasTrasCierre;

    /** Corre cada 15 minutos; idempotente, opera sobre todos los tenants. */
    @Scheduled(fixedDelayString = "PT15M", initialDelayString = "PT1M")
    @Transactional
    public void expirarPendientes() {
        OffsetDateTime limite = OffsetDateTime.now(clock).minusHours(expiracionHoras);
        List<Reserva> vencidas = reservaRepository
                .findByEstadoAndCreadaEnBefore(EstadoReserva.PENDIENTE, limite);

        for (Reserva reserva : vencidas) {
            cancelarPorExpiracion(reserva,
                    "[Expiración] Cancelada automáticamente tras %d h sin cotización.".formatted(expiracionHoras));
        }
        if (!vencidas.isEmpty()) {
            log.info("Job de expiración: {} solicitudes pendientes canceladas", vencidas.size());
        }
    }

    /**
     * Falla #1 (revisión a 2 años): una COTIZADA cuyo cliente desapareció retenía su bloque
     * EN_ESPERA para siempre — sábados "ocupados" por cotizaciones fantasma de hace meses.
     * Pasados N días sin confirmación, se cancela y el bloque vuelve a la venta.
     */
    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT2M")
    @Transactional
    public void expirarCotizadas() {
        OffsetDateTime limite = OffsetDateTime.now(clock).minusDays(cotizacionExpiracionDias);
        List<Reserva> vencidas = reservaRepository
                .findByEstadoAndCotizadaEnBefore(EstadoReserva.COTIZADA, limite);

        for (Reserva reserva : vencidas) {
            cancelarPorExpiracion(reserva,
                    "[Expiración] Cotización sin respuesta tras %d días.".formatted(cotizacionExpiracionDias));
        }
        if (!vencidas.isEmpty()) {
            log.info("Job de expiración: {} cotizaciones sin respuesta canceladas", vencidas.size());
        }
    }

    /**
     * Falla #2 (revisión a 2 años): el estado REALIZADA era inalcanzable — toda fiesta concretada
     * quedaba CONFIRMADA (es decir, "activa") para siempre, bloqueando la anonimización del
     * cliente y ensuciando los reportes. Barrido diario: confirmadas con fecha ya pasada.
     */
    @Scheduled(cron = "0 15 4 * * *")
    @Transactional
    public void realizarConcluidas() {
        LocalDate hoy = LocalDate.now(clock);
        List<Reserva> concluidas = reservaRepository
                .findByEstadoConBloqueAnterior(EstadoReserva.CONFIRMADA, hoy);

        for (Reserva reserva : concluidas) {
            reserva.transicionarA(EstadoReserva.REALIZADA);
        }
        if (!concluidas.isEmpty()) {
            log.info("Job de cierre: {} reservas confirmadas marcadas REALIZADA", concluidas.size());
        }
    }

    /**
     * Mantenimiento diario (04:30): purga refresh tokens revocados/expirados.
     * Sin esto la tabla crece sin límite — cada login y cada rotación insertan una fila.
     */
    @Scheduled(cron = "0 30 4 * * *")
    @Transactional
    public void purgarRefreshTokens() {
        int eliminados = refreshTokenRepository.purgarInvalidos(OffsetDateTime.now(clock));
        if (eliminados > 0) {
            log.info("Mantenimiento: {} refresh tokens purgados", eliminados);
        }
    }

    /**
     * Falla #4 (revisión a 2 años, Ley 21.719 — minimización): clientes sin actividad por más
     * de la retención configurada se anonimizan (la fila se conserva: las reservas históricas
     * la referencian, pero deja de contener datos personales). Mensual, día 1 a las 05:00.
     * Un cliente con reservas activas todavía no es candidato — por eso depende del barrido
     * de {@link #realizarConcluidas()}.
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
            usuarioRepository.eliminarDeTenant(id);
            tenantRepository.delete(tenant);
            log.info("Offboarding: tenant '{}' (#{}) purgado físicamente ({} días tras su cierre)",
                    tenant.getSlug(), id, purgaDiasTrasCierre);
        }
    }

    private void cancelarPorExpiracion(Reserva reserva, String nota) {
        reserva.transicionarA(EstadoReserva.CANCELADA);
        String previos = reserva.getComentarios() == null ? "" : reserva.getComentarios() + "\n";
        reserva.setComentarios(previos + nota);
        bloqueRepository.transicionarEstado(reserva.getBloqueId(), reserva.getTenantId(),
                EstadoBloque.EN_ESPERA, EstadoBloque.DISPONIBLE);
        log.info("Reserva #{} expirada; bloque {} liberado", reserva.getId(), reserva.getBloqueId());
    }
}
