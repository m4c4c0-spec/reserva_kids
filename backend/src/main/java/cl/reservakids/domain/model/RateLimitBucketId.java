package cl.reservakids.domain.model;

import java.io.Serializable;
import java.util.Objects;

public class RateLimitBucketId implements Serializable {

    private String ip;
    private String rutaTipo;

    public RateLimitBucketId() {}

    public RateLimitBucketId(String ip, String rutaTipo) {
        this.ip = ip;
        this.rutaTipo = rutaTipo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RateLimitBucketId that = (RateLimitBucketId) o;
        return Objects.equals(ip, that.ip) && Objects.equals(rutaTipo, that.rutaTipo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ip, rutaTipo);
    }
}
