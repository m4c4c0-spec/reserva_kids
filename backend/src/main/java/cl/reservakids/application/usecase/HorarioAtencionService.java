package cl.reservakids.application.usecase;

import cl.reservakids.application.dto.HorarioAtencionDtos.*;
import cl.reservakids.domain.model.AuditEvent;
import cl.reservakids.domain.model.HorarioAtencion;
import cl.reservakids.domain.model.Tenant;
import cl.reservakids.domain.repository.HorarioAtencionRepository;
import cl.reservakids.domain.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuración del horario de atención para el agendamiento por hora.
 * El dueño reemplaza todas las franjas de una vez (operación poco frecuente);
 * el intervalo entre slots vive en el tenant.
 */
@Service
@RequiredArgsConstructor
public class HorarioAtencionService {

    private final TenantRepository tenantRepository;
    private final HorarioAtencionRepository horarioRepository;
    private final AuditPort audit;

    @Transactional(readOnly = true)
    public HorarioResponse obtener(Long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();
        List<FranjaResponse> franjas = horarioRepository
                .findByTenantIdOrderByDiaSemanaAscHoraAperturaAsc(tenantId)
                .stream().map(FranjaResponse::de).toList();
        return new HorarioResponse(tenant.getIntervaloMin(), franjas);
    }

    @Transactional
    public void guardar(Long tenantId, Long usuarioId, HorarioRequest req) {
        if (req.intervaloMin() < 5 || req.intervaloMin() > 120) {
            throw new IllegalArgumentException("El intervalo debe estar entre 5 y 120 minutos");
        }
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();
        tenant.setIntervaloMin(req.intervaloMin());

        // Reemplazo completo: el dueño envía el horario semanal entero.
        horarioRepository.deleteByTenantId(tenantId);
        List<HorarioAtencion> nuevos = new ArrayList<>();
        for (FranjaRequest f : req.franjas()) {
            if (!f.horaCierre().isAfter(f.horaApertura())) {
                throw new IllegalArgumentException(
                        "La hora de cierre debe ser posterior a la de apertura (día " + f.diaSemana() + ")");
            }
            HorarioAtencion h = new HorarioAtencion();
            h.setTenantId(tenantId);
            h.setDiaSemana(f.diaSemana().shortValue());
            h.setHoraApertura(f.horaApertura());
            h.setHoraCierre(f.horaCierre());
            nuevos.add(h);
        }
        horarioRepository.saveAll(nuevos);
        audit.registrar(tenantId, usuarioId, AuditEvent.ACTOR_DUENO,
                AuditEvent.CONFIGURACION_GUARDAR, "CONFIGURACION", null,
                "Horario actualizado: " + req.franjas().size() + " franjas");
    }

    /**
     * Horario por defecto para un tenant recién creado: L-V 09:00-18:00, Sáb 10:00-14:00.
     * El agendamiento por hora depende de tener horarios; sin esto, un negocio nuevo no
     * aparecería en el directorio de clientes.
     */
    @Transactional
    public void crearHorarioPorDefecto(Long tenantId) {
        List<HorarioAtencion> franjas = new ArrayList<>();
        for (int dia = 1; dia <= 5; dia++) {
            franjas.add(franja(tenantId, dia, "09:00", "18:00"));
        }
        franjas.add(franja(tenantId, 6, "10:00", "14:00"));
        horarioRepository.saveAll(franjas);
    }

    private static HorarioAtencion franja(Long tenantId, int dia, String apertura, String cierre) {
        HorarioAtencion h = new HorarioAtencion();
        h.setTenantId(tenantId);
        h.setDiaSemana((short) dia);
        h.setHoraApertura(LocalTime.parse(apertura));
        h.setHoraCierre(LocalTime.parse(cierre));
        return h;
    }
}
