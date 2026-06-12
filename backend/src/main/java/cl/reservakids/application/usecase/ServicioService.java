package cl.reservakids.application.usecase;

import cl.reservakids.application.dto.ServicioDtos.ServicioRequest;
import cl.reservakids.application.dto.ServicioDtos.ServicioResponse;
import cl.reservakids.domain.exception.RecursoNoEncontradoException;
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

    @Transactional(readOnly = true)
    public List<ServicioResponse> listar(Long tenantId) {
        return servicioRepository.findByTenantIdOrderByNombre(tenantId)
                .stream().map(ServicioResponse::de).toList();
    }

    @Transactional
    public ServicioResponse crear(Long tenantId, ServicioRequest req) {
        Servicio servicio = new Servicio();
        servicio.setTenantId(tenantId);
        aplicar(servicio, req);
        return ServicioResponse.de(servicioRepository.save(servicio));
    }

    @Transactional
    public ServicioResponse actualizar(Long tenantId, Long id, ServicioRequest req) {
        Servicio servicio = buscar(tenantId, id);
        aplicar(servicio, req);
        return ServicioResponse.de(servicio);
    }

    /** Borrado lógico: las reservas históricas siguen referenciando el servicio. */
    @Transactional
    public void desactivar(Long tenantId, Long id) {
        buscar(tenantId, id).setActivo(false);
    }

    private Servicio buscar(Long tenantId, Long id) {
        return servicioRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Servicio no encontrado"));
    }

    private void aplicar(Servicio servicio, ServicioRequest req) {
        servicio.setNombre(req.nombre());
        servicio.setDescripcion(req.descripcion());
        servicio.setPrecioClp(req.precioClp());
        servicio.setDuracionMin(req.duracionMin());
        servicio.setCapacidad(req.capacidad());
        if (req.activo() != null) {
            servicio.setActivo(req.activo());
        }
    }
}
