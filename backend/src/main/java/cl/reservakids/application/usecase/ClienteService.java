package cl.reservakids.application.usecase;

import cl.reservakids.domain.exception.RecursoNoEncontradoException;
import cl.reservakids.domain.model.Cliente;
import cl.reservakids.domain.model.EstadoReserva;
import cl.reservakids.domain.model.Reserva;
import cl.reservakids.domain.repository.ClienteRepository;
import cl.reservakids.domain.repository.ReservaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;

/**
 * Falla #4 (revisión a 2 años, Ley 21.719): derecho de supresión a demanda.
 * Cuando un apoderado pide "bórrenme de su base", el dueño lo ejecuta desde el panel:
 * la fila se conserva (las reservas históricas la referencian) pero deja de contener
 * datos personales. Complementa la anonimización automática por inactividad
 * de {@link ExpiracionService#anonimizarInactivos()}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final ReservaRepository reservaRepository;
    private final Clock clock;

    @Transactional
    public void anonimizar(Long tenantId, Long clienteId) {
        Cliente cliente = clienteRepository.findByIdAndTenantId(clienteId, tenantId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cliente no encontrado"));
        if (cliente.isAnonimizado()) {
            return; // idempotente: pedir dos veces la supresión no es un error
        }
        if (reservaRepository.existsByClienteIdAndEstadoIn(clienteId, EstadoReserva.ACTIVOS)) {
            throw new IllegalArgumentException(
                    "El cliente tiene reservas activas; cancélalas o complétalas antes de eliminar sus datos");
        }
        cliente.anonimizar(OffsetDateTime.now(clock));
        // Falla 3.2 (revisión a 5 años): sin esto, "[Contacto: ...]" y los motivos de
        // cancelación conservaban datos personales — la supresión quedaba a medias.
        int limpiadas = reservaRepository.anonimizarComentariosDeCliente(
                clienteId, Reserva.COMENTARIOS_ANONIMIZADOS);
        log.info("Ley 21.719: cliente #{} anonimizado a solicitud del titular (tenant {}); "
                + "comentarios limpiados en {} reservas", clienteId, tenantId, limpiadas);
    }
}
