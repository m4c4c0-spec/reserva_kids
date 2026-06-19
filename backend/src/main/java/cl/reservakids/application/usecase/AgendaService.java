package cl.reservakids.application.usecase;

import cl.reservakids.application.dto.AgendaDtos.AgendarCitaRequest;
import cl.reservakids.application.dto.AgendaDtos.AgendarCitaResponse;
import cl.reservakids.domain.exception.ConflictoBloqueException;
import cl.reservakids.domain.exception.RecursoNoEncontradoException;
import cl.reservakids.domain.model.*;
import cl.reservakids.domain.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Agendamiento de citas por hora (cliente autenticado). Valida los servicios y la hora contra
 * la disponibilidad real (el precio y la duración se recalculan en el backend, nunca se confían
 * al cliente), crea la cita en estado PENDIENTE_PAGO reservando la franja y genera la preferencia
 * de pago. La cita solo se confirma cuando el webhook de Mercado Pago aprueba el pago
 * ({@link ReservaService#procesarWebhookPago}): "pago = agendado".
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgendaService {

    private final TenantRepository tenantRepository;
    private final ServicioRepository servicioRepository;
    private final CuentaClienteRepository cuentaClienteRepository;
    private final ClienteRepository clienteRepository;
    private final ReservaRepository reservaRepository;
    private final ReservaServicioRepository reservaServicioRepository;
    private final DisponibilidadService disponibilidadService;
    private final PasarelaPagoPort pasarelaPagoPort;
    private final Clock clock;

    @Transactional
    public AgendarCitaResponse agendar(Long cuentaClienteId, String slug, AgendarCitaRequest req) {
        Tenant tenant = tenantRepository.findBySlugAndEstado(slug, Tenant.ESTADO_ACTIVO)
                .orElseThrow(() -> new RecursoNoEncontradoException("Negocio no encontrado"));

        CuentaCliente cuenta = cuentaClienteRepository.findById(cuentaClienteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cuenta no encontrada"));
        if (cuenta.getTelefono() == null || cuenta.getTelefono().isBlank()) {
            throw new IllegalArgumentException("Agrega tu teléfono a tu cuenta para poder agendar");
        }

        // Servicios: deben existir, ser del negocio y estar activos. Precio/duración salen del
        // catálogo (servidor), nunca del cliente.
        List<Servicio> servicios = req.servicioIds().stream()
                .distinct()
                .map(id -> servicioRepository.findByIdAndTenantId(id, tenant.getId())
                        .filter(Servicio::isActivo)
                        .orElseThrow(() -> new RecursoNoEncontradoException("Servicio no disponible: " + id)))
                .toList();
        for (Servicio s : servicios) {
            if (s.getDuracionMin() == null || s.getDuracionMin() <= 0) {
                throw new IllegalArgumentException(
                        "El servicio '" + s.getNombre() + "' no tiene duración configurada y no se puede agendar");
            }
        }
        int duracionTotal = servicios.stream().mapToInt(Servicio::getDuracionMin).sum();
        int total = servicios.stream().mapToInt(Servicio::getPrecioClp).sum();

        // La hora debe seguir libre (otro cliente pudo tomarla mientras tanto).
        if (!disponibilidadService.horasLibres(tenant.getId(), req.fecha(), duracionTotal).contains(req.hora())) {
            throw new ConflictoBloqueException("Esa hora ya no está disponible, elige otra");
        }

        OffsetDateTime inicio = req.fecha().atTime(req.hora()).atZone(clock.getZone()).toOffsetDateTime();
        OffsetDateTime fin = inicio.plusMinutes(duracionTotal);

        // Cliente tenant-scoped (contacto + Ley 21.719): reusa el de este negocio por teléfono o
        // lo crea desde la cuenta. Es lo que alimenta el WhatsApp/correo de confirmación.
        OffsetDateTime ahora = OffsetDateTime.now(clock);
        Cliente cliente = clienteRepository
                .findByTenantIdAndTelefono(tenant.getId(), cuenta.getTelefono())
                .orElseGet(() -> {
                    Cliente nuevo = new Cliente();
                    nuevo.setTenantId(tenant.getId());
                    nuevo.setTelefono(cuenta.getTelefono());
                    return nuevo;
                });
        cliente.setNombre(cuenta.getNombre() != null ? cuenta.getNombre() : "Cliente");
        if (cuenta.getEmail() != null) {
            cliente.setEmail(cuenta.getEmail());
        }
        cliente.setConsentimientoEn(ahora);
        cliente.setUltimaActividadEn(ahora);
        cliente = clienteRepository.save(cliente);

        Reserva reserva = new Reserva();
        reserva.setTenantId(tenant.getId());
        reserva.setClienteId(cliente.getId());
        reserva.setCuentaClienteId(cuenta.getId());
        reserva.setInicio(inicio);
        reserva.setFin(fin);
        reserva.setEstado(EstadoReserva.PENDIENTE_PAGO);
        reserva.setTotalClp(total);
        reserva.setSeniaClp(total); // pago = agendado: se cobra el total (el adaptador usa seniaClp)
        reserva = reservaRepository.saveAndFlush(reserva);

        for (Servicio s : servicios) {
            reservaServicioRepository.save(new ReservaServicio(reserva.getId(), s));
        }

        // Pago obligatorio: sin pasarela no hay forma de confirmar la cita. Si MP falla, la
        // excepción revierte toda la transacción (no quedan citas huérfanas PENDIENTE_PAGO).
        PasarelaPagoPort.PreferenciaPagoResponse pref = pasarelaPagoPort.crearPreferenciaDePago(reserva, tenant);
        if (pref == null) {
            throw new IllegalArgumentException("Este negocio aún no tiene pagos en línea habilitados");
        }
        reserva.setMpPreferenceId(pref.preferenceId());
        reserva.setMpInitPoint(pref.initPoint());

        log.info("Cita #{} creada (PENDIENTE_PAGO) para tenant {} el {} — total {}",
                reserva.getId(), slug, inicio, total);
        return new AgendarCitaResponse(reserva.getId(), pref.initPoint(), total, inicio.toString());
    }
}
