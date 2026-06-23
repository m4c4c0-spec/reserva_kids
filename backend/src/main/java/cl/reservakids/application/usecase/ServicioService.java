package cl.reservakids.application.usecase;

import cl.reservakids.application.dto.ServicioDtos.ServicioRequest;
import cl.reservakids.application.dto.ServicioDtos.ServicioResponse;
import cl.reservakids.domain.exception.RecursoNoEncontradoException;
import cl.reservakids.domain.model.AuditEvent;
import cl.reservakids.domain.model.Servicio;
import cl.reservakids.domain.repository.ServicioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** RF-02: CRUD de servicios/paquetes del tenant. */
@Service
@RequiredArgsConstructor
public class ServicioService {

    private final ServicioRepository servicioRepository;
    private final AuditPort audit;
    private final cl.reservakids.infrastructure.security.HtmlSanitizer sanitizer;

    @Transactional(readOnly = true)
    public List<ServicioResponse> listar(Long tenantId) {
        return servicioRepository.findByTenantIdOrderByNombre(tenantId)
                .stream().map(ServicioResponse::de).toList();
    }

    @Transactional
    public ServicioResponse crear(Long tenantId, Long usuarioId, ServicioRequest req) {
        Servicio servicio = new Servicio();
        servicio.setTenantId(tenantId);
        aplicar(servicio, req);
        servicio = servicioRepository.save(servicio);
        audit.registrar(tenantId, usuarioId, AuditEvent.ACTOR_DUENO,
                AuditEvent.SERVICIO_CREAR, "SERVICIO", servicio.getId(), req.nombre());
        return ServicioResponse.de(servicio);
    }

    @Transactional
    public ServicioResponse actualizar(Long tenantId, Long usuarioId, Long id, ServicioRequest req) {
        Servicio servicio = buscar(tenantId, id);
        aplicar(servicio, req);
        audit.registrar(tenantId, usuarioId, AuditEvent.ACTOR_DUENO,
                AuditEvent.SERVICIO_EDITAR, "SERVICIO", id, req.nombre());
        return ServicioResponse.de(servicio);
    }

    /** Borrado lógico: las reservas históricas siguen referenciando el servicio. */
    @Transactional
    public void desactivar(Long tenantId, Long usuarioId, Long id) {
        Servicio s = buscar(tenantId, id);
        s.setActivo(false);
        audit.registrar(tenantId, usuarioId, AuditEvent.ACTOR_DUENO,
                AuditEvent.SERVICIO_DESACTIVAR, "SERVICIO", id, s.getNombre());
    }

    private Servicio buscar(Long tenantId, Long id) {
        return servicioRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Servicio no encontrado"));
    }

    private void aplicar(Servicio servicio, ServicioRequest req) {
        servicio.setNombre(sanitizer.sanitize(req.nombre()));
        servicio.setDescripcion(sanitizer.sanitize(req.descripcion()));
        servicio.setPrecioClp(req.precioClp());
        servicio.setDuracionMin(req.duracionMin());
        servicio.setCapacidad(req.capacidad());
        if (req.activo() != null) {
            servicio.setActivo(req.activo());
        }
        if (req.esAdicional() != null) {
            servicio.setEsAdicional(req.esAdicional());
        }
        if (req.stock() != null) {
            servicio.setStock(req.stock() == 0 ? null : req.stock());
        }
    }
}
