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
import java.util.List;

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
        OffsetDateTime ahora = OffsetDateTime.now(clock);

        // ── Citas por hora (inicio entre ahora+23h y ahora+24h) ──
        OffsetDateTime desde = ahora.plusHours(horasAntes - 1);
        OffsetDateTime hasta = ahora.plusHours(horasAntes);

        List<Reserva> citas = reservaRepository.findCitasConfirmadasProximas(desde, hasta);
        for (Reserva cita : citas) {
            procesarCita(cita);
        }

        // ── Cumpleaños (bloque.fecha = mañana) ──
        LocalDate manana = LocalDate.now(clock).plusDays(1);
        List<Reserva> cumpleanos = reservaRepository.findCumpleanosConfirmadosEnFecha(manana);
        for (Reserva r : cumpleanos) {
            procesarCumpleano(r);
        }

        if (!citas.isEmpty() || !cumpleanos.isEmpty()) {
            log.info("Recordatorios enviados: {} citas, {} cumpleaños", citas.size(), cumpleanos.size());
        }
    }

    private void procesarCita(Reserva cita) {
        Tenant tenant = tenantRepository.findById(cita.getTenantId()).orElse(null);
        if (tenant == null || !tenant.isActivo()) return;

        Cliente cliente = clienteRepository
                .findByIdAndTenantId(cita.getClienteId(), cita.getTenantId()).orElse(null);
        if (cliente == null || cliente.isAnonimizado()) return;

        List<ReservaServicio> servicios = reservaServicioRepository.findByReservaIdOrderById(cita.getId());
        if (cliente.getEmail() != null && !cliente.getEmail().isBlank()) {
            notificacion.recordatorio(tenant, cita, cliente, servicios);
        }
    }

    private void procesarCumpleano(Reserva reserva) {
        Tenant tenant = tenantRepository.findById(reserva.getTenantId()).orElse(null);
        if (tenant == null || !tenant.isActivo()) return;

        Cliente cliente = clienteRepository
                .findByIdAndTenantId(reserva.getClienteId(), reserva.getTenantId()).orElse(null);
        if (cliente == null || cliente.isAnonimizado()) return;

        List<ReservaServicio> servicios = null;
        if (reserva.getServicioId() != null) {
            servicios = reservaServicioRepository.findByReservaIdOrderById(reserva.getId());
        }
        if (cliente.getEmail() != null && !cliente.getEmail().isBlank()) {
            notificacion.recordatorio(tenant, reserva, cliente, servicios);
        }
    }
}
