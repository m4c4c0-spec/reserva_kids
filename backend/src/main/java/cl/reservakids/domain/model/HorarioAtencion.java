package cl.reservakids.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

/**
 * Franja de atención de un negocio para un día de la semana. Junto con
 * {@code tenant.intervalo_min} y la duración de los servicios elegidos, alimenta el cálculo
 * de horas libres (DisponibilidadService). Un día puede tener varias franjas (ej. mañana y
 * tarde con cierre al mediodía).
 */
@Entity
@Table(name = "horario_atencion")
@Getter
@Setter
@NoArgsConstructor
public class HorarioAtencion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    /** ISO-8601: 1=Lunes .. 7=Domingo (coincide con {@link java.time.DayOfWeek#getValue()}). */
    @Column(name = "dia_semana", nullable = false)
    private Short diaSemana;

    @Column(name = "hora_apertura", nullable = false)
    private LocalTime horaApertura;

    @Column(name = "hora_cierre", nullable = false)
    private LocalTime horaCierre;
}
