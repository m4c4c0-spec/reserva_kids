package cl.reservakids.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "invitado")
@Getter
@Setter
@NoArgsConstructor
public class Invitado {

    public static final String PENDIENTE = "PENDIENTE";
    public static final String CONFIRMADO = "CONFIRMADO";
    public static final String RECHAZADO = "RECHAZADO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "reserva_id", nullable = false)
    private Long reservaId;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(length = 160)
    private String email;

    @Column(length = 20)
    private String telefono;

    @Column(nullable = false, length = 20)
    private String estado = PENDIENTE;

    @Column(nullable = false, unique = true, length = 64)
    private String token;

    @Column(length = 500)
    private String comentarios;

    @Column(name = "creado_en", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime creadoEn;
}
