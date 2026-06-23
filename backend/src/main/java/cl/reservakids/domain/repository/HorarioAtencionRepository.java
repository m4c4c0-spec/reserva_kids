package cl.reservakids.domain.repository;

import cl.reservakids.domain.model.HorarioAtencion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HorarioAtencionRepository extends JpaRepository<HorarioAtencion, Long> {

    /** Franjas de un día concreto (ISO: 1=Lun..7=Dom) — base del cálculo de horas libres. */
    List<HorarioAtencion> findByTenantIdAndDiaSemana(Long tenantId, Short diaSemana);

    /** Todas las franjas del negocio (para mostrar/editar el horario en el panel del dueño). */
    List<HorarioAtencion> findByTenantIdOrderByDiaSemanaAscHoraAperturaAsc(Long tenantId);

    /** Reemplazo completo del horario semanal. */
    void deleteByTenantId(Long tenantId);

    /** ¿El negocio ya definió al menos una franja de atención? (estado de onboarding). */
    boolean existsByTenantId(Long tenantId);
}
