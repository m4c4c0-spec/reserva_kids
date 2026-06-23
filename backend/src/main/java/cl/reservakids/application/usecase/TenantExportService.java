package cl.reservakids.application.usecase;

import cl.reservakids.application.dto.TenantDtos.*;
import cl.reservakids.domain.exception.RecursoNoEncontradoException;
import cl.reservakids.domain.model.Tenant;
import cl.reservakids.domain.repository.BloqueDisponibleRepository;
import cl.reservakids.domain.repository.ClienteRepository;
import cl.reservakids.domain.repository.PagoRepository;
import cl.reservakids.domain.repository.ReservaRepository;
import cl.reservakids.domain.repository.ServicioRepository;
import cl.reservakids.domain.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;

/**
 * Export completo de un tenant — portabilidad (Ley 21.719) y a la vez el insumo del cierre
 * responsable (falla 5.1). Extraído de {@link TenantService} para separar la lectura masiva
 * del offboarding y bajar el acoplamiento de cada servicio.
 */
@Service
@RequiredArgsConstructor
public class TenantExportService {

    private final TenantRepository tenantRepository;
    private final ServicioRepository servicioRepository;
    private final ClienteRepository clienteRepository;
    private final BloqueDisponibleRepository bloqueRepository;
    private final ReservaRepository reservaRepository;
    private final PagoRepository pagoRepository;
    private final Clock clock;

    /**
     * Export completo del tenant. Disponible siempre, no solo al cerrar. Cuando lo invoca
     * {@link TenantService#cerrar} (otra bean, dentro de su transacción de escritura) se
     * une a esa transacción y refleja el estado final ya escrito.
     */
    @Transactional(readOnly = true)
    public ExportResponse exportar(Long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Negocio no encontrado"));
        return new ExportResponse(
                tenant.getNombre(), tenant.getSlug(), tenant.getPlan(), tenant.getEstado(),
                tenant.getCreadoEn(), tenant.getCerradoEn(), OffsetDateTime.now(clock),
                servicioRepository.findByTenantIdOrderByNombre(tenantId).stream().map(ServicioExport::de).toList(),
                clienteRepository.findByTenantIdOrderByNombre(tenantId).stream().map(ClienteExport::de).toList(),
                bloqueRepository.findByTenantIdOrderByFechaAscHoraInicioAsc(tenantId).stream().map(BloqueExport::de).toList(),
                reservaRepository.findByTenantIdOrderByCreadaEnDesc(tenantId).stream().map(ReservaExport::de).toList(),
                pagoRepository.findDeTenant(tenantId).stream().map(PagoExport::de).toList());
    }
}
