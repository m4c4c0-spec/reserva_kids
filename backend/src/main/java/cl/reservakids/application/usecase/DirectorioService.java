package cl.reservakids.application.usecase;

import cl.reservakids.application.dto.ClienteDtos.NegocioResumen;
import cl.reservakids.domain.model.Tenant;
import cl.reservakids.domain.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Directorio para clientes: negocios activos agendables (con horario y servicios activos). */
@Service
@RequiredArgsConstructor
public class DirectorioService {

    private final TenantRepository tenantRepository;

    @Transactional(readOnly = true)
    public List<NegocioResumen> negociosConDisponibilidad() {
        return tenantRepository.findAgendables(Tenant.ESTADO_ACTIVO)
                .stream()
                .map(t -> new NegocioResumen(t.getSlug(), t.getNombre()))
                .toList();
    }
}
