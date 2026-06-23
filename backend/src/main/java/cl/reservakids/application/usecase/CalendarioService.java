package cl.reservakids.application.usecase;

import cl.reservakids.application.dto.CalendarioDtos.BloqueRequest;
import cl.reservakids.application.dto.CalendarioDtos.BloqueResponse;
import cl.reservakids.domain.exception.ConflictoBloqueException;
import cl.reservakids.domain.exception.RecursoNoEncontradoException;
import cl.reservakids.domain.model.AuditEvent;
import cl.reservakids.domain.model.BloqueDisponible;
import cl.reservakids.domain.model.EstadoBloque;
import cl.reservakids.domain.repository.BloqueDisponibleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/** RF-04: el dueño define bloques disponibles; el sistema bloquea fechas reservadas. */
@Service
@RequiredArgsConstructor
public class CalendarioService {

    private final BloqueDisponibleRepository bloqueRepository;
    private final AuditPort audit;
    private final Clock clock; // zona horaria del negocio (America/Santiago), no la del servidor

    @Transactional(readOnly = true)
    public List<BloqueResponse> listarMes(Long tenantId, YearMonth mes) {
        return bloqueRepository
                .findByTenantIdAndFechaBetweenOrderByFechaAscHoraInicioAsc(
                        tenantId, mes.atDay(1), mes.atEndOfMonth())
                .stream().map(BloqueResponse::de).toList();
    }

    @Transactional(readOnly = true)
    public List<BloqueResponse> listarDisponiblesMes(Long tenantId, YearMonth mes) {
        LocalDate hoy = LocalDate.now(clock);
        LocalDate desde = mes.atDay(1).isBefore(hoy) ? hoy : mes.atDay(1);
        return bloqueRepository
                .findByTenantIdAndEstadoAndFechaBetweenOrderByFechaAscHoraInicioAsc(
                        tenantId, EstadoBloque.DISPONIBLE, desde, mes.atEndOfMonth())
                .stream().map(BloqueResponse::de).toList();
    }

    @Transactional
    public BloqueResponse crear(Long tenantId, Long usuarioId, BloqueRequest req) {
        if (!req.horaFin().isAfter(req.horaInicio())) {
            throw new IllegalArgumentException("La hora de fin debe ser posterior a la de inicio");
        }
        if (req.fecha().isBefore(LocalDate.now(clock))) {
            throw new IllegalArgumentException("No se pueden crear bloques en el pasado");
        }
        BloqueDisponible bloque = new BloqueDisponible();
        bloque.setTenantId(tenantId);
        bloque.setFecha(req.fecha());
        bloque.setHoraInicio(req.horaInicio());
        bloque.setHoraFin(req.horaFin());
        try {
            bloque = bloqueRepository.saveAndFlush(bloque);
            audit.registrar(tenantId, usuarioId, AuditEvent.ACTOR_DUENO,
                    AuditEvent.BLOQUE_CREAR, "BLOQUE", bloque.getId(),
                    req.fecha() + " " + req.horaInicio() + "-" + req.horaFin());
            return BloqueResponse.de(bloque);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictoBloqueException("Ya existe un bloque en esa fecha y hora");
        }
    }

    @Transactional
    public void eliminar(Long tenantId, Long usuarioId, Long id) {
        BloqueDisponible bloque = bloqueRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Bloque no encontrado"));
        if (bloque.getEstado() != EstadoBloque.DISPONIBLE) {
            throw new ConflictoBloqueException("No se puede eliminar un bloque con reserva asociada");
        }
        bloqueRepository.delete(bloque);
        audit.registrar(tenantId, usuarioId, AuditEvent.ACTOR_DUENO,
                AuditEvent.BLOQUE_ELIMINAR, "BLOQUE", id,
                bloque.getFecha() + " " + bloque.getHoraInicio() + "-" + bloque.getHoraFin());
    }
}
