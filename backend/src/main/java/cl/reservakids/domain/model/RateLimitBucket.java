package cl.reservakids.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "rate_limit_bucket")
@IdClass(RateLimitBucketId.class)
@Getter
@Setter
@NoArgsConstructor
public class RateLimitBucket {

    @Id
    @Column(length = 45, nullable = false)
    private String ip;

    @Id
    @Column(name = "ruta_tipo", length = 50, nullable = false)
    private String rutaTipo;

    @Column(name = "epoch_minuto", nullable = false)
    private Long epochMinuto;

    @Column(name = "contador", nullable = false)
    private Integer contador;

    @Column(name = "max_permitido", nullable = false)
    private Integer maxPermitido;

    @Column(name = "creado_en", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime creadoEn;

    public RateLimitBucket(String ip, String rutaTipo, Long epochMinuto, Integer contador, Integer maxPermitido) {
        this.ip = ip;
        this.rutaTipo = rutaTipo;
        this.epochMinuto = epochMinuto;
        this.contador = contador;
        this.maxPermitido = maxPermitido;
    }
}
