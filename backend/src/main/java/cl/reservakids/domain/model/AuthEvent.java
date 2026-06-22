package cl.reservakids.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "auth_event")
@Getter
@Setter
@NoArgsConstructor
public class AuthEvent {

    public static final String LOGIN = "LOGIN";
    public static final String LOGIN_FAIL = "LOGIN_FAIL";
    public static final String REFRESH = "REFRESH";
    public static final String THEFT_DETECTED = "THEFT_DETECTED";
    public static final String LOGOUT = "LOGOUT";
    public static final String RESET_REQUEST = "RESET_REQUEST";
    public static final String RESET_COMPLETE = "RESET_COMPLETE";
    public static final String MAGIC_LINK_REQUEST = "MAGIC_LINK_REQUEST";
    public static final String MAGIC_LOGIN = "MAGIC_LOGIN";

    public static final String SUCCESS = "SUCCESS";
    public static final String FAILURE = "FAILURE";

    // Actor types
    public static final String ACTOR_DUENO = "DUENO";
    public static final String ACTOR_CLIENTE = "CLIENTE";
    public static final String ACTOR_ADMIN = "ADMIN";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_type", nullable = false)
    private String actorType;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "email_hash", nullable = false)
    private String emailHash;

    @Column(nullable = false)
    private String accion;

    @Column(nullable = false)
    private String resultado;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(length = 500)
    private String detalle;

    @Column(name = "creado_en", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime creadoEn;

    public AuthEvent(String actorType, Long actorId, String emailHash, String accion,
                     String resultado, String ipAddress, String userAgent, String detalle) {
        this.actorType = actorType;
        this.actorId = actorId;
        this.emailHash = emailHash;
        this.accion = accion;
        this.resultado = resultado;
        this.ipAddress = ipAddress;
        this.userAgent = truncar(userAgent, 500);
        this.detalle = truncar(detalle, 500);
    }

    private static String truncar(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen - 3) + "...";
    }
}
