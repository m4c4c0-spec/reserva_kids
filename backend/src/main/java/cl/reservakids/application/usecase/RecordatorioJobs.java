package cl.reservakids.application.usecase;

import cl.reservakids.domain.model.Cliente;
import cl.reservakids.domain.model.Reserva;
import cl.reservakids.domain.model.ReservaServicio;
import cl.reservakids.domain.model.Tenant;
import cl.reservakids.domain.repository.ClienteRepository;
import cl.reservakids.domain.repository.ReservaRepository;
import cl.reservakids.domain.repository.ReservaServicioRepository;
import cl.reservakids.domain.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Recordatorios automáticos 24h antes del evento (RNF-08).
 * Cada hora busca citas y cumpleaños confirmados que ocurren dentro de exactamente
 * 24 horas y envía un email al cliente listando los servicios contratados y la hora.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecordatorioJobs {

    private final ReservaRepository reservaRepository;
    private final ClienteRepository clienteRepository;
    private final TenantRepository tenantRepository;
    private final ReservaServicioRepository reservaServicioRepository;
    private final NotificacionPort notificacion;
    private final DistributedLockPort distributedLock;
    private final Clock clock;

    @Value("${app.recordatorio.horas-antes:24}")
    private int horasAntes;

    /**
     * Cada hora busca eventos que ocurren exactamente en {@code horasAntes} horas.
     * Ventana de 1 hora: [ahora + (horasAntes - 1), ahora + horasAntes).
     * Así cada reserva recibe el recordatorio UNA sola vez.
     */
    @Scheduled(fixedDelayString = "PT30M", initialDelayString = "PT2M")
    @Transactional(readOnly = true)
    public void enviarRecordatorios() {
        if (!distributedLock.tryAcquire("enviarRecordatorios")) return;
        OffsetDateTime ahora = OffsetDateTime.now(clock);

        // ── Citas por hora (inicio entre ahora+23h y ahora+24h) ──
        OffsetDateTime desde = ahora.plusHours(horasAntes - 1);
        OffsetDateTime hasta = ahora.plusHours(horasAntes);

        List<Reserva> citas = reservaRepository.findCitasConfirmadasProximas(desde, hasta);

        // Batch-load tenants y clientes: 1 query cada uno en vez de N
        Map<Long, Tenant> tenants = batchTenants(citas);
        Map<Long, Cliente> clientes = batchClientes(citas);

        for (Reserva cita : citas) {
            try {
                procesarCita(cita, tenants, clientes);
            } catch (Exception e) {
                log.error("Error al procesar recordatorio de cita #{}: {}", cita.getId(), e.getMessage());
            }
        }

        // ── Cumpleaños (bloque.fecha = mañana) ──
        LocalDate manana = LocalDate.now(clock).plusDays(1);
        List<Reserva> cumpleanos = reservaRepository.findCumpleanosConfirmadosEnFecha(manana);
        if (!cumpleanos.isEmpty()) {
            // Batch-load tenants y clientes para cumpleaños también
            tenants = batchTenants(cumpleanos);
            clientes = batchClientes(cumpleanos);
        }
        for (Reserva r : cumpleanos) {
            try {
                procesarCumpleano(r, tenants, clientes);
            } catch (Exception e) {
                log.error("Error al procesar recordatorio de cumpleaños #{}: {}", r.getId(), e.getMessage());
            }
        }

        if (!citas.isEmpty() || !cumpleanos.isEmpty()) {
            log.info("Recordatorios enviados: {} citas, {} cumpleaños", citas.size(), cumpleanos.size());
        }
    }

    private Map<Long, Tenant> batchTenants(List<Reserva> reservas) {
        Set<Long> ids = reservas.stream().map(Reserva::getTenantId).collect(Collectors.toSet());
        if (ids.isEmpty()) return Collections.emptyMap();
        return tenantRepository.findAllById(ids).stream()
                .filter(Tenant::isActivo)
                .collect(Collectors.toMap(Tenant::getId, t -> t));
    }

    private Map<Long, Cliente> batchClientes(List<Reserva> reservas) {
        Set<Long> ids = reservas.stream().map(Reserva::getClienteId).collect(Collectors.toSet());
        if (ids.isEmpty()) return Collections.emptyMap();
        return clienteRepository.findAllById(ids).stream()
                .filter(c -> !c.isAnonimizado())
                .collect(Collectors.toMap(Cliente::getId, c -> c));
    }

    private void procesarCita(Reserva cita, Map<Long, Tenant> tenants, Map<Long, Cliente> clientes) {
        Tenant tenant = tenants.get(cita.getTenantId());
        if (tenant == null) return;

        Cliente cliente = clientes.get(cita.getClienteId());
        if (cliente == null) return;

        List<ReservaServicio> servicios = reservaServicioRepository.findByReservaIdOrderById(cita.getId());
        if (cliente.getEmail() != null && !cliente.getEmail().isBlank()) {
            notificacion.recordatorio(tenant, cita, cliente, servicios);
        }
    }

    private void procesarCumpleano(Reserva reserva, Map<Long, Tenant> tenants, Map<Long, Cliente> clientes) {
        Tenant tenant = tenants.get(reserva.getTenantId());
        if (tenant == null) return;

        Cliente cliente = clientes.get(reserva.getClienteId());
        if (cliente == null) return;

        List<ReservaServicio> servicios = null;
        if (reserva.getServicioId() != null) {
            servicios = reservaServicioRepository.findByReservaIdOrderById(reserva.getId());
        }
        if (cliente.getEmail() != null && !cliente.getEmail().isBlank()) {
            notificacion.recordatorio(tenant, reserva, cliente, servicios);
        }
    }
}
