package cl.reservakids.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(name = "idempotency_key")
@Getter
@NoArgsConstructor
public class IdempotencyKey {

    @Id
    @Column(length = 64, nullable = false)
    private String key;

    @Column(length = 255, nullable = false)
    private String endpoint;

    @Column(name = "creado_en", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime creadoEn;

    public IdempotencyKey(String key, String endpoint) {
        this.key = key;
        this.endpoint = endpoint;
    }
}
