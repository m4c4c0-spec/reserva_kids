package cl.reservakids.application.usecase;

import cl.reservakids.application.dto.ReservaDtos.*;
import cl.reservakids.domain.exception.ConflictoBloqueException;
import cl.reservakids.domain.exception.RecursoNoEncontradoException;
import cl.reservakids.domain.model.*;
import cl.reservakids.domain.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Casos de uso del flujo de reserva (SDLC §4.4):
 * CrearReserva (público) → CotizarReserva → ConfirmarReserva → RegistrarPago / Cancelar.
 */
@Service
@RequiredArgsConstructor
public class ReservaService {

    private final TenantRepository tenantRepository;
    private final ServicioRepository servicioRepository;
    private final BloqueDisponibleRepository bloqueRepository;
    private final ClienteRepository clienteRepository;
    private final ReservaRepository reservaRepository;
    private final PagoRepository pagoRepository;
    private final NotificacionPort notificacion;

    /**
     * RF-05 + RNF-05: solicitud pública. La toma del bloque es un UPDATE atómico
     * condicionado a estado DISPONIBLE; el índice único parcial de BD es la segunda barrera.
     */
    @Transactional
    public ReservaResponse crearSolicitudPublica(String slug, SolicitudPublicaRequest req) {
        Tenant tenant = tenantRepository.findBySlugAndEstado(slug, "ACTIVO")
                .orElseThrow(() -> new RecursoNoEncontradoException("Negocio no encontrado"));

        Servicio servicio = servicioRepository.findByIdAndTenantId(req.servicioId(), tenant.getId())
                .filter(Servicio::isActivo)
                .orElseThrow(() -> new RecursoNoEncontradoException("Servicio no encontrado"));

        int filas = bloqueRepository.transicionarEstado(
                req.bloqueId(), tenant.getId(), EstadoBloque.DISPONIBLE, EstadoBloque.EN_ESPERA);
        if (filas == 0) {
            throw new ConflictoBloqueException(
                    "El bloque ya no está disponible. Por favor elige otra fecha cercana.");
        }

        OffsetDateTime ahora = OffsetDateTime.now();
        // Falla #9 (2 años): sin normalizar, cada formato de teléfono crea un cliente distinto
        String telefono = Cliente.normalizarTelefono(req.telefono());
        Cliente cliente = clienteRepository
                .findByTenantIdAndTelefono(tenant.getId(), telefono)
                .orElseGet(() -> {
                    Cliente nuevo = new Cliente();
                    nuevo.setTenantId(tenant.getId());
                    nuevo.setTelefono(telefono);
                    nuevo.setNombre(req.nombreContacto());
                    if (req.email() != null && !req.email().isBlank()) {
                        nuevo.setEmail(req.email());
                    }
                    return nuevo;
                });
        // Cliente existente: este endpoint es público y sin auth — nunca sobrescribe
        // datos ya guardados (cualquiera que conozca el teléfono podría reescribirlos).
        // Solo rellena vacíos; si el contacto difiere, queda anotado en la reserva.
        // Falla 3.2 (5 años): esa anotación es dato personal en texto libre — la
        // anonimización del cliente reemplaza los comentarios enteros por eso mismo.
        String contactoDistinto = null;
        if (cliente.getId() != null) {
            if (cliente.getEmail() == null && req.email() != null && !req.email().isBlank()) {
                cliente.setEmail(req.email());
            }
            if (!cliente.getNombre().equalsIgnoreCase(req.nombreContacto())) {
                contactoDistinto = req.nombreContacto();
            }
        }
        // Ley 21.719: el envío del formulario (checkbox obligatorio) renueva consentimiento
        // y actividad — base del plazo de retención.
        cliente.setConsentimientoEn(ahora);
        cliente.setUltimaActividadEn(ahora);
        cliente = clienteRepository.save(cliente);

        Reserva reserva = new Reserva();
        reserva.setTenantId(tenant.getId());
        reserva.setClienteId(cliente.getId());
        reserva.setServicioId(servicio.getId());
        reserva.setBloqueId(req.bloqueId());
        reserva.setNumNinos(req.numNinos());
        reserva.setComuna(req.comuna());
        reserva.setComentarios(contactoDistinto == null ? req.comentarios()
                : "[Contacto: %s]%s".formatted(contactoDistinto,
                        req.comentarios() == null ? "" : "\n" + req.comentarios()));
        try {
            reserva = reservaRepository.saveAndFlush(reserva);
        } catch (DataIntegrityViolationException e) {
            // RNF-05: índice único parcial — otra transacción ganó la carrera
            throw new ConflictoBloqueException(
                    "El bloque ya no está disponible. Por favor elige otra fecha cercana.");
        }

        notificacion.nuevaSolicitud(tenant, reserva, cliente);
        return respuesta(reserva, cliente);
    }

    /**
     * Fix #7 (revisión de código): antes cada reserva de la página disparaba 2 queries
     * (total pagado + cliente) — 41 queries por página de 20. Ahora: 3 en total.
     */
    @Transactional(readOnly = true)
    public Page<ReservaResponse> listar(Long tenantId, EstadoReserva estado, Pageable pageable) {
        Page<Reserva> pagina = estado == null
                ? reservaRepository.findByTenantIdOrderByCreadaEnDesc(tenantId, pageable)
                : reservaRepository.findByTenantIdAndEstadoOrderByCreadaEnDesc(tenantId, estado, pageable);
        if (pagina.isEmpty()) {
            return Page.empty(pageable);
        }
        List<Long> reservaIds = pagina.map(Reserva::getId).toList();
        Map<Long, Integer> pagados = pagoRepository.totalesPagadosPorReserva(reservaIds).stream()
                .collect(Collectors.toMap(f -> (Long) f[0], f -> ((Number) f[1]).intValue()));
        Map<Long, Cliente> clientes = clienteRepository
                .findAllById(pagina.map(Reserva::getClienteId).toSet()).stream()
                .collect(Collectors.toMap(Cliente::getId, c -> c));

        return pagina.map(r -> {
            Cliente cliente = clientes.get(r.getClienteId());
            String link = cliente == null ? null : notificacion.linkWhatsApp(cliente, r);
            return ReservaResponse.de(r, pagados.getOrDefault(r.getId(), 0), link);
        });
    }

    /** RF-06: dueño envía cotización (total + seña sugerida). */
    @Transactional
    public ReservaResponse cotizar(Long tenantId, Long id, CotizarRequest req) {
        if (req.seniaClp() > req.totalClp()) {
            throw new IllegalArgumentException("La seña no puede superar el total");
        }
        Reserva reserva = buscar(tenantId, id);
        reserva.transicionarA(EstadoReserva.COTIZADA);
        reserva.setTotalClp(req.totalClp());
        reserva.setSeniaClp(req.seniaClp());
        reserva.setCotizadaEn(OffsetDateTime.now()); // base de la expiración de cotizaciones
        return respuesta(reserva, null);
    }

    /** RF-06/07: confirma la reserva (normalmente tras registrar la seña). */
    @Transactional
    public ReservaResponse confirmar(Long tenantId, Long id) {
        Reserva reserva = buscar(tenantId, id);
        reserva.transicionarA(EstadoReserva.CONFIRMADA);
        bloqueRepository.transicionarEstado(
                reserva.getBloqueId(), tenantId, EstadoBloque.EN_ESPERA, EstadoBloque.CONFIRMADO);
        return respuesta(reserva, null);
    }

    /**
     * Falla #2 (revisión a 2 años): cierre manual CONFIRMADA → REALIZADA desde el panel.
     * El barrido diario de ExpiracionService cubre las que el dueño olvide cerrar.
     */
    @Transactional
    public ReservaResponse realizar(Long tenantId, Long id) {
        Reserva reserva = buscar(tenantId, id);
        reserva.transicionarA(EstadoReserva.REALIZADA);
        return respuesta(reserva, null);
    }

    @Transactional
    public ReservaResponse cancelar(Long tenantId, Long id, CancelarRequest req) {
        Reserva reserva = buscar(tenantId, id);
        EstadoBloque estadoBloque = reserva.getEstado() == EstadoReserva.CONFIRMADA
                ? EstadoBloque.CONFIRMADO : EstadoBloque.EN_ESPERA;
        reserva.transicionarA(EstadoReserva.CANCELADA);
        if (req != null && req.motivo() != null && !req.motivo().isBlank()) {
            String previos = reserva.getComentarios() == null ? "" : reserva.getComentarios() + "\n";
            reserva.setComentarios(previos + "[Cancelación] " + req.motivo());
        }
        // libera el bloque para nuevas solicitudes
        bloqueRepository.transicionarEstado(
                reserva.getBloqueId(), tenantId, estadoBloque, EstadoBloque.DISPONIBLE);
        return respuesta(reserva, null);
    }

    /** RF-07: registro de seña/abono (o devolución) y saldo pendiente. */
    @Transactional
    public ReservaResponse registrarPago(Long tenantId, Long usuarioId, Long id, PagoRequest req) {
        Reserva reserva = buscar(tenantId, id);
        boolean devolucion = Pago.TIPO_DEVOLUCION.equals(req.tipo());
        // Abonos solo en reservas activas; devoluciones también tras una cancelación
        // (caso típico: se devuelve la seña de un cumpleaños cancelado).
        boolean estadoPermitido = EstadoReserva.ACTIVOS.contains(reserva.getEstado())
                || (devolucion && reserva.getEstado() == EstadoReserva.CANCELADA);
        if (!estadoPermitido) {
            throw new IllegalArgumentException("No se pueden registrar pagos en una reserva " + reserva.getEstado());
        }
        // Fix #4 (revisión de código): sin este tope, una devolución podía superar lo pagado
        // → total pagado negativo y saldo mayor al total (libro contable corrupto).
        if (devolucion && pagoRepository.totalPagado(reserva.getId()) < req.montoClp()) {
            throw new IllegalArgumentException("No se puede devolver más de lo pagado");
        }
        Pago pago = new Pago();
        pago.setReservaId(reserva.getId());
        pago.setMontoClp(req.montoClp());
        pago.setTipo(devolucion ? Pago.TIPO_DEVOLUCION : Pago.TIPO_ABONO);
        pago.setMedio(req.medio());
        pago.setComprobanteUrl(req.comprobanteUrl());
        pago.setRegistradoPor(usuarioId); // auditoría: quién lo anotó
        pagoRepository.save(pago);
        return respuesta(reserva, null);
    }

    private Reserva buscar(Long tenantId, Long id) {
        return reservaRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Reserva no encontrada"));
    }

    private ReservaResponse respuesta(Reserva reserva, Cliente clienteConocido) {
        int pagado = reserva.getId() == null ? 0 : pagoRepository.totalPagado(reserva.getId());
        Cliente cliente = clienteConocido != null ? clienteConocido
                : clienteRepository.findById(reserva.getClienteId()).orElse(null);
        String link = cliente == null ? null : notificacion.linkWhatsApp(cliente, reserva);
        return ReservaResponse.de(reserva, pagado, link);
    }
}
