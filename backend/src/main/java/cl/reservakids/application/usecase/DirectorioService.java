package cl.reservakids.application.usecase;

import cl.reservakids.application.dto.ClienteDtos.NegocioResumen;
import cl.reservakids.domain.model.EstadoBloque;
import cl.reservakids.domain.model.Tenant;
import cl.reservakids.domain.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/** Directorio para clientes: negocios activos con horarios disponibles. */
@Service
@RequiredArgsConstructor
public class DirectorioService {

    private final TenantRepository tenantRepository;
    private final Clock clock; // "hoy" en hora del negocio (America/Santiago)

    @Transactional(readOnly = true)
    public List<NegocioResumen> negociosConDisponibilidad() {
        return tenantRepository.findActivosConDisponibilidad(
                        Tenant.ESTADO_ACTIVO, EstadoBloque.DISPONIBLE, LocalDate.now(clock))
                .stream()
                .map(t -> new NegocioResumen(t.getSlug(), t.getNombre()))
                .toList();
    }
}
