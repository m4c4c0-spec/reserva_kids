package cl.reservakids.application;

import cl.reservakids.application.usecase.DisponibilidadService;
import cl.reservakids.domain.model.HorarioAtencion;
import cl.reservakids.domain.model.Reserva;
import cl.reservakids.domain.model.Tenant;
import cl.reservakids.domain.repository.HorarioAtencionRepository;
import cl.reservakids.domain.repository.ReservaRepository;
import cl.reservakids.domain.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/** Motor de disponibilidad: genera horas según horario+duración y resta las citas ocupadas. */
@ExtendWith(MockitoExtension.class)
class DisponibilidadServiceTest {

    @Mock HorarioAtencionRepository horarioRepository;
    @Mock TenantRepository tenantRepository;
    @Mock ReservaRepository reservaRepository;

    private static final ZoneId ZONA = ZoneId.of("America/Santiago");
    // Jueves 2026-06-18 ~09:00 en Santiago → el viernes 19 es futuro (sin filtro de pasado).
    private final Clock clock = Clock.fixed(Instant.parse("2026-06-18T13:00:00Z"), ZONA);
    private final LocalDate VIERNES = LocalDate.of(2026, 6, 19);

    private DisponibilidadService service;

    @BeforeEach
    void setUp() {
        service = new DisponibilidadService(horarioRepository, tenantRepository, reservaRepository, clock);
    }

    /** Franja L-V 09:00–18:00, intervalo 30, sin citas. */
    private void horarioViernes() {
        HorarioAtencion h = new HorarioAtencion();
        h.setHoraApertura(LocalTime.of(9, 0));
        h.setHoraCierre(LocalTime.of(18, 0));
        when(horarioRepository.findByTenantIdAndDiaSemana(1L, (short) 5)).thenReturn(List.of(h));
        Tenant t = new Tenant();
        t.setIntervaloMin(30);
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(t));
    }

    @Test
    void generaSlotsSegunDuracion() {
        horarioViernes();
        when(reservaRepository.findCitasEntre(eq(1L), any(), any(), any())).thenReturn(List.of());

        List<LocalTime> libres = service.horasLibres(1L, VIERNES, 240); // 4 h

        assertEquals(LocalTime.of(9, 0), libres.get(0));
        assertEquals(LocalTime.of(14, 0), libres.get(libres.size() - 1)); // 14:00 + 4h = 18:00
        assertEquals(11, libres.size());
    }

    @Test
    void restaLasCitasOcupadas() {
        horarioViernes();
        Reserva cita = new Reserva();
        cita.setInicio(VIERNES.atTime(10, 0).atZone(ZONA).toOffsetDateTime());
        cita.setFin(VIERNES.atTime(11, 0).atZone(ZONA).toOffsetDateTime());
        when(reservaRepository.findCitasEntre(eq(1L), any(), any(), any())).thenReturn(List.of(cita));

        List<LocalTime> libres = service.horasLibres(1L, VIERNES, 60);

        assertTrue(libres.contains(LocalTime.of(9, 0)), "09:00 termina justo al inicio de la cita, libre");
        assertFalse(libres.contains(LocalTime.of(9, 30)), "09:30 solapa la cita");
        assertFalse(libres.contains(LocalTime.of(10, 0)), "10:00 es la cita");
        assertFalse(libres.contains(LocalTime.of(10, 30)), "10:30 solapa la cita");
        assertTrue(libres.contains(LocalTime.of(11, 0)), "11:00 arranca al terminar la cita, libre");
    }

    @Test
    void duracionCeroNoOfreceHoras() {
        assertTrue(service.horasLibres(1L, VIERNES, 0).isEmpty());
    }

    @Test
    void fechaPasadaNoOfreceHoras() {
        assertTrue(service.horasLibres(1L, LocalDate.of(2026, 6, 17), 60).isEmpty());
    }
}
