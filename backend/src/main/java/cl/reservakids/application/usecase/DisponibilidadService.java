package cl.reservakids.application.usecase;

import cl.reservakids.domain.model.EstadoReserva;
import cl.reservakids.domain.model.HorarioAtencion;
import cl.reservakids.domain.model.Reserva;
import cl.reservakids.domain.model.Tenant;
import cl.reservakids.domain.repository.HorarioAtencionRepository;
import cl.reservakids.domain.repository.ReservaRepository;
import cl.reservakids.domain.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Motor de disponibilidad para agendamiento por hora. Dada una fecha y la duración total de
 * los servicios elegidos, devuelve las horas de inicio libres: caben dentro de una franja de
 * {@link HorarioAtencion}, respetan la granularidad {@code tenant.intervalo_min}, no quedan en
 * el pasado y no solapan con citas ya tomadas.
 *
 * <p>Todo el cálculo es en minutos-del-día (int) para evitar los wraps de {@link LocalTime} al
 * sumar duraciones largas. MVP: un solo recurso por negocio (la capacidad paralela por
 * profesional/silla queda como evolución futura).
 */
@Service
@RequiredArgsConstructor
public class DisponibilidadService {

    private final HorarioAtencionRepository horarioRepository;
    private final TenantRepository tenantRepository;
    private final ReservaRepository reservaRepository;
    private final Clock clock; // "hoy/ahora" en hora del negocio (America/Santiago)

    /** Intervalo ocupado del día, en minutos-del-día [inicio, fin). */
    public record Ocupado(int inicioMin, int finMin) {}

    @Transactional(readOnly = true)
    public List<LocalTime> horasLibres(Long tenantId, LocalDate fecha, int duracionTotalMin) {
        if (duracionTotalMin <= 0) {
            return List.of();
        }
        LocalDate hoy = LocalDate.now(clock);
        if (fecha.isBefore(hoy)) {
            return List.of(); // no se agenda en el pasado
        }

        int intervalo = tenantRepository.findById(tenantId)
                .map(Tenant::getIntervaloMin).orElse(30);
        short dia = (short) fecha.getDayOfWeek().getValue(); // 1=Lun .. 7=Dom
        // Si la fecha es hoy, descartar las horas que ya pasaron.
        int corteMin = fecha.equals(hoy) ? LocalTime.now(clock).toSecondOfDay() / 60 : -1;
        List<Ocupado> ocupados = intervalosOcupados(tenantId, fecha);

        List<LocalTime> libres = new ArrayList<>();
        for (HorarioAtencion franja : horarioRepository.findByTenantIdAndDiaSemana(tenantId, dia)) {
            agregarHorasDeFranja(franja, duracionTotalMin, intervalo, corteMin, ocupados, libres);
        }
        return libres.stream().distinct().sorted().toList();
    }

    /** Recorre una franja en pasos de {@code intervalo} y agrega las horas de inicio libres. */
    private void agregarHorasDeFranja(HorarioAtencion franja, int duracionTotalMin, int intervalo,
                                      int corteMin, List<Ocupado> ocupados, List<LocalTime> libres) {
        int apertura = franja.getHoraApertura().toSecondOfDay() / 60;
        int cierre = franja.getHoraCierre().toSecondOfDay() / 60;
        for (int inicio = apertura; inicio + duracionTotalMin <= cierre; inicio += intervalo) {
            int fin = inicio + duracionTotalMin;
            // inicio > corteMin descarta lo ya pasado (solo aplica si la fecha es hoy).
            if (inicio > corteMin && !solapaConOcupados(inicio, fin, ocupados)) {
                libres.add(LocalTime.ofSecondOfDay(inicio * 60L));
            }
        }
    }

    /** ¿El intervalo [inicio, fin) pisa alguna cita ya tomada? */
    private static boolean solapaConOcupados(int inicio, int fin, List<Ocupado> ocupados) {
        for (Ocupado o : ocupados) {
            if (inicio < o.finMin() && fin > o.inicioMin()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Intervalos ya tomados del día: citas PENDIENTE_PAGO (hora retenida mientras se paga) y
     * CONFIRMADA. Las marcas inicio/fin son instantes; se convierten a la hora local del negocio
     * para comparar contra el horario de atención.
     */
    private List<Ocupado> intervalosOcupados(Long tenantId, LocalDate fecha) {
        ZoneId zona = clock.getZone();
        OffsetDateTime desde = fecha.atStartOfDay(zona).toOffsetDateTime();
        OffsetDateTime hasta = fecha.plusDays(1).atStartOfDay(zona).toOffsetDateTime();
        List<Reserva> citas = reservaRepository.findCitasEntre(
                tenantId, desde, hasta, List.of(EstadoReserva.PENDIENTE_PAGO, EstadoReserva.CONFIRMADA));
        List<Ocupado> ocupados = new ArrayList<>();
        for (Reserva c : citas) {
            int ini = c.getInicio().atZoneSameInstant(zona).toLocalTime().toSecondOfDay() / 60;
            int fin = c.getFin().atZoneSameInstant(zona).toLocalTime().toSecondOfDay() / 60;
            ocupados.add(new Ocupado(ini, fin));
        }
        return ocupados;
    }
}
