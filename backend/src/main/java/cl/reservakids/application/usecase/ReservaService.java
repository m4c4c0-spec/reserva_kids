package cl.reservakids.application.usecase;

import cl.reservakids.application.dto.ReservaDtos.*;
import cl.reservakids.domain.exception.ConflictoBloqueException;
import cl.reservakids.domain.exception.RecursoNoEncontradoException;
import cl.reservakids.domain.model.*;
import cl.reservakids.domain.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservaService {

    private final TenantRepository tenantRepository;
    private final ServicioRepository servicioRepository;
    private final BloqueDisponibleRepository bloqueRepository;
    private final ClienteRepository clienteRepository;
    private final ReservaRepository reservaRepository;
    private final ReservaServicioRepository reservaServicioRepository;
    private final PagoRepository pagoRepository;
    private final NotificacionPort notificacion;
    private final NotificacionWhatsappPort whatsapp;
    private final PasarelaPagoPort pasarelaPagoPort;
    private final AuditPort audit;
    private final cl.reservakids.infrastructure.security.HtmlSanitizer sanitizer;

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
        String rutNormalizado = RutValidator.normalizar(req.rut());
        Cliente cliente = clienteRepository
                .findByTenantIdAndTelefono(tenant.getId(), telefono)
                .orElseGet(() -> {
                    Cliente nuevo = new Cliente();
                    nuevo.setTenantId(tenant.getId());
                    nuevo.setTelefono(telefono);
                    nuevo.setNombre(req.nombreContacto());
                    nuevo.setRut(rutNormalizado);
                    if (req.email() != null && !req.email().isBlank()) {
                        nuevo.setEmail(req.email());
                    }
                    return nuevo;
                });
        // Cliente existente: este endpoint es público y sin auth — nunca sobrescribe
        // datos ya guardados (cualquiera que conozca el teléfono podría reescribirlos).
        // Solo rellena vacíos; el RUT se registra la primera vez (si no existe ya)
        // para que el dueño tenga identidad verificada del cliente existente.
        // Si el cliente ya tiene RUT y es distinto → posible error de dedup o suplantación.
        String contactoDistinto = null;
        if (cliente.getId() != null) {
            if (cliente.getEmail() == null && req.email() != null && !req.email().isBlank()) {
                cliente.setEmail(req.email());
            }
            if (cliente.getRut() == null && rutNormalizado != null) {
                cliente.setRut(rutNormalizado);
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
        reserva.setComuna(sanitizer.sanitize(req.comuna()));
        reserva.setComentarios(sanitizer.sanitize(construirComentarios(contactoDistinto, req.comentarios())));
        try {
            reserva = reservaRepository.saveAndFlush(reserva);
        } catch (DataIntegrityViolationException e) {
            // RNF-05: índice único parcial — otra transacción ganó la carrera
            throw new ConflictoBloqueException(
                    "El bloque ya no está disponible. Por favor elige otra fecha cercana.");
        }

        notificacion.nuevaSolicitud(tenant, reserva, cliente);
        audit.registrar(tenant.getId(), cliente.getId(), AuditEvent.ACTOR_CLIENTE,
                AuditEvent.SOLICITUD_CREAR, "RESERVA", reserva.getId(),
                "Solicitud pública: " + servicio.getNombre());
        return respuesta(reserva, cliente);
    }

    /**
     * Fix #7 (revisión de código): antes cada reserva de la página disparaba 2 queries
     * (total pagado + cliente) — 41 queries por página de 20. Ahora: 3 en total.
     */
    @Transactional(readOnly = true)
    public Page<ReservaResponse> listar(Long tenantId, EstadoReserva estado, String q, Pageable pageable) {
        String termino = q == null ? "" : q.trim();
        Page<Reserva> pagina;
        if (termino.isEmpty()) {
            pagina = estado == null
                    ? reservaRepository.findByTenantIdOrderByCreadaEnDesc(tenantId, pageable)
                    : reservaRepository.findByTenantIdAndEstadoOrderByCreadaEnDesc(tenantId, estado, pageable);
        } else {
            // Patrón LIKE armado en Java (no CONCAT) para que el bind se tipe como texto.
            String patron = "%" + termino.toLowerCase() + "%";
            // Si el término es numérico, también busca por #reserva exacto; si no, -1 nunca matchea.
            Long reservaId = parsearIdOrNull(termino);
            pagina = estado == null
                    ? reservaRepository.buscar(tenantId, reservaId, patron, pageable)
                    : reservaRepository.buscarPorEstado(tenantId, estado, reservaId, patron, pageable);
        }
        return enriquecer(pagina, pageable);
    }

    /** Carga totales pagados y clientes en lote (3 queries en total) y arma la respuesta. */
    private Page<ReservaResponse> enriquecer(Page<Reserva> pagina, Pageable pageable) {
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

    private static Long parsearIdOrNull(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    /**
     * Sprint 2 §2.2: historial de reservas del cliente autenticado (apoderado). A diferencia
     * de {@link #listar}, cruza tenants — el apoderado agenda en varios negocios y todas sus
     * citas (identificadas por {@code cuenta_cliente_id}) aparecen en su panel. Mismo patrón
     * anti-N+1: 3 queries (página + totales pagados + clientes para el link de WhatsApp).
     */
    @Transactional(readOnly = true)
    public Page<ReservaResponse> listarReservasDeCliente(Long cuentaClienteId, EstadoReserva estado, Pageable pageable) {
        Page<Reserva> pagina = estado == null
                ? reservaRepository.findByCuentaClienteIdOrderByCreadaEnDesc(cuentaClienteId, pageable)
                : reservaRepository.findByCuentaClienteIdAndEstadoOrderByCreadaEnDesc(cuentaClienteId, estado, pageable);
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
    public ReservaResponse cotizar(Long tenantId, Long usuarioId, Long id, CotizarRequest req) {
        if (req.seniaClp() > req.totalClp()) {
            throw new IllegalArgumentException("La seña no puede superar el total");
        }
        Reserva reserva = buscar(tenantId, id);
        reserva.transicionarA(EstadoReserva.COTIZADA);
        reserva.setTotalClp(req.totalClp());
        reserva.setSeniaClp(req.seniaClp());
        reserva.setCotizadaEn(OffsetDateTime.now()); // base de la expiración de cotizaciones

        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();
        if (tenant.tienePasarelaConfigurada()) {
            // B2: el link de pago es OPCIONAL. Una caída de la pasarela no debe tumbar la
            // cotización (antes la RuntimeException del adaptador hacía rollback de todo → 500).
            // Si falla, la cotización se guarda igual sin link; el dueño puede cobrar la seña por
            // otro medio o reintentar editando la cotización.
            try {
                PasarelaPagoPort.PreferenciaPagoResponse pref = pasarelaPagoPort.crearPreferenciaDePago(reserva, tenant);
                if (pref != null) {
                    reserva.setMpPreferenceId(pref.preferenceId());
                    reserva.setMpInitPoint(pref.initPoint());
                }
            } catch (RuntimeException e) {
                log.warn("Cotización #{}: no se pudo generar el link de pago ({}); "
                        + "la cotización se guarda sin link", reserva.getId(), e.getMessage());
            }
        }

        audit.registrar(tenantId, usuarioId, AuditEvent.ACTOR_DUENO,
                AuditEvent.RESERVA_COTIZAR, "RESERVA", reserva.getId(),
                "Total: $" + req.totalClp() + " Seña: $" + req.seniaClp());
        return respuesta(reserva, null);
    }

    /** RF-06/07: confirma la reserva (normalmente tras registrar la seña). */
    @Transactional
    public ReservaResponse confirmar(Long tenantId, Long usuarioId, Long id) {
        return confirmarInterno(tenantId, id, usuarioId);
    }

    /**
     * Cuerpo de {@link #confirmar} sin {@code @Transactional}: lo invoca también
     * {@link #procesarWebhookPago}, que ya corre en su propia transacción. Llamarlo
     * directo evita la auto-invocación que omitiría el proxy (Sonar S6809).
     */
    private ReservaResponse confirmarInterno(Long tenantId, Long id, Long actorId) {
        Reserva reserva = buscar(tenantId, id);
        reserva.transicionarA(EstadoReserva.CONFIRMADA);
        bloqueRepository.transicionarEstado(
                reserva.getBloqueId(), tenantId, EstadoBloque.EN_ESPERA, EstadoBloque.CONFIRMADO);
        if (actorId != null) {
            audit.registrar(tenantId, actorId, AuditEvent.ACTOR_DUENO,
                    AuditEvent.RESERVA_CONFIRMAR, "RESERVA", reserva.getId(), null);
        }
        return respuesta(reserva, null);
    }

    /**
     * Falla #2 (revisión a 2 años): cierre manual CONFIRMADA → REALIZADA desde el panel.
     * El barrido diario de CicloReservaJobs cubre las que el dueño olvide cerrar.
     */
    @Transactional
    public ReservaResponse realizar(Long tenantId, Long usuarioId, Long id) {
        Reserva reserva = buscar(tenantId, id);
        reserva.transicionarA(EstadoReserva.REALIZADA);
        audit.registrar(tenantId, usuarioId, AuditEvent.ACTOR_DUENO,
                AuditEvent.RESERVA_REALIZAR, "RESERVA", reserva.getId(), null);
        return respuesta(reserva, null);
    }

    @Transactional
    public ReservaResponse cancelar(Long tenantId, Long usuarioId, Long id, CancelarRequest req) {
        Reserva reserva = buscar(tenantId, id);
        EstadoBloque estadoBloque = reserva.getEstado() == EstadoReserva.CONFIRMADA
                ? EstadoBloque.CONFIRMADO : EstadoBloque.EN_ESPERA;
        reserva.transicionarA(EstadoReserva.CANCELADA);
        if (req != null && req.motivo() != null && !req.motivo().isBlank()) {
            String previos = reserva.getComentarios() == null ? "" : reserva.getComentarios() + "\n";
            reserva.setComentarios(previos + "[Cancelación] " + sanitizer.sanitize(req.motivo()));
        }
        // libera el bloque para nuevas solicitudes
        bloqueRepository.transicionarEstado(
                reserva.getBloqueId(), tenantId, estadoBloque, EstadoBloque.DISPONIBLE);
        audit.registrar(tenantId, usuarioId, AuditEvent.ACTOR_DUENO,
                AuditEvent.RESERVA_CANCELAR, "RESERVA", reserva.getId(),
                req != null ? req.motivo() : null);
        return respuesta(reserva, null);
    }

    /** RF-07: registro de seña/abono (o devolución) y saldo pendiente. */
    @Transactional
    public ReservaResponse registrarPago(Long tenantId, Long usuarioId, Long id, PagoRequest req) {
        return registrarPagoInterno(tenantId, usuarioId, usuarioId, id, req);
    }

    /**
     * Cuerpo de {@link #registrarPago} sin {@code @Transactional}: lo invoca también
     * {@link #procesarWebhookPago}, que ya corre en su propia transacción. Llamarlo
     * directo evita la auto-invocación que omitiría el proxy (Sonar S6809).
     */
    private ReservaResponse registrarPagoInterno(Long tenantId, Long actorId, Long usuarioId, Long id, PagoRequest req) {
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
        pago.setReferenciaExterna(req.referenciaExterna());
        pagoRepository.save(pago);
        if (actorId != null) {
            audit.registrar(tenantId, actorId, AuditEvent.ACTOR_DUENO,
                    AuditEvent.PAGO_REGISTRAR, "PAGO", pago.getId(),
                    (devolucion ? "Devolución" : "Abono") + " $" + req.montoClp() + " " + req.medio());
        }
        return respuesta(reserva, null);
    }

    /** Procesa webhook IPN de Mercado Pago / notificación de Khipu. {@code medio} = "MERCADOPAGO"|"KHIPU". */
    @Transactional
    public void procesarWebhookPago(Long tenantId, Long reservaId, String paymentId, Integer montoPagado,
                                     String estado, String medio) {
        Reserva reserva = buscar(tenantId, reservaId);

        // Si el pago ya fue registrado antes (idempotencia)
        if (pagoRepository.existsByReferenciaExterna(paymentId)) {
            return;
        }

        if ("approved".equals(estado)) {
            // PagoRequest(montoClp, medio, comprobanteUrl, tipo, referenciaExterna) —
            // el orden importa: antes se registraba medio="ABONO" y tipo=null.
            PagoRequest req = new PagoRequest(montoPagado, medio, null, Pago.TIPO_ABONO, paymentId);
            registrarPagoInterno(tenantId, null, null, reservaId, req);

            // El pago aprobado confirma tanto la cotización de cumpleaños (COTIZADA) como la
            // cita por hora (PENDIENTE_PAGO → CONFIRMADA = "pago = agendado"). confirmar() hace
            // no-op del bloque cuando es null (las citas no usan bloque). Otros estados (ya
            // CONFIRMADA/REALIZADA) se ignoran: el pago quedó registrado arriba, idempotente.
            // Confirmar un estado inválido lanzaría TransicionInvalidaException → 500 → MP
            // reintentaría el webhook para siempre, por eso se acota a estos dos estados.
            if (reserva.getEstado() == EstadoReserva.COTIZADA
                    || reserva.getEstado() == EstadoReserva.PENDIENTE_PAGO) {
                boolean esCita = reserva.getInicio() != null; // cita por hora (no cumpleaños)
                confirmarInterno(tenantId, reservaId, null);
                if (esCita) {
                    notificarCitaConfirmada(reserva);
                }
            } else {
                log.info("Webhook MP: pago {} registrado en reserva #{} (estado {}); sin transición",
                        paymentId, reservaId, reserva.getEstado());
            }
        }
    }

    /** Tras confirmar una cita por hora: avisa al cliente por email y WhatsApp (stub por ahora). */
    private void notificarCitaConfirmada(Reserva cita) {
        Tenant tenant = tenantRepository.findById(cita.getTenantId()).orElse(null);
        // Defense-in-depth (§5.6): la cita ya se obtuvo tenant-scoped, pero usamos
        // findByIdAndTenantId para que un clienteId huérfano/apuntando a otro tenant
        // no se lea aquí tampoco.
        Cliente cliente = clienteRepository
                .findByIdAndTenantId(cita.getClienteId(), cita.getTenantId()).orElse(null);
        if (tenant == null || cliente == null) {
            return;
        }
        List<ReservaServicio> servicios = reservaServicioRepository.findByReservaIdOrderById(cita.getId());
        notificacion.citaConfirmada(tenant, cita, cliente, servicios);
        whatsapp.confirmacionReserva(tenant, cita, cliente, servicios);
    }

    /** Antepone el contacto alternativo (si lo hay) a los comentarios del cliente. */
    private static String construirComentarios(String contactoDistinto, String comentarios) {
        if (contactoDistinto == null) {
            return comentarios;
        }
        String extra = comentarios == null ? "" : "\n" + comentarios;
        return "[Contacto: %s]%s".formatted(contactoDistinto, extra);
    }

    private Reserva buscar(Long tenantId, Long id) {
        return reservaRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Reserva no encontrada"));
    }

    private ReservaResponse respuesta(Reserva reserva, Cliente clienteConocido) {
        int pagado = reserva.getId() == null ? 0 : pagoRepository.totalPagado(reserva.getId());
        // Defense-in-depth (§5.6): mismo criterio que notificarCitaConfirmada —
        // findByIdAndTenantId aunque el reserva.getClienteId() venga de una reserva ya tenant-scoped.
        Cliente cliente = clienteConocido != null ? clienteConocido
                : clienteRepository.findByIdAndTenantId(reserva.getClienteId(), reserva.getTenantId())
                        .orElse(null);
        String link = cliente == null ? null : notificacion.linkWhatsApp(cliente, reserva);
        return ReservaResponse.de(reserva, pagado, link);
    }
}
