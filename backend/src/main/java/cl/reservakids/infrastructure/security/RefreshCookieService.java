package cl.reservakids.infrastructure.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Construye y borra la cookie HttpOnly que transporta el refresh token.
 * <p>
 * Mover el refresh token del body (accesible por JS) a una cookie HttpOnly cierra la
 * ventana de robo vía XSS: el navegador la envía automáticamente al /refresh y el
 * código de la página nunca la ve. SameSite=Lax mitiga CSRF cross-site en POST (la
 * cookie no se envía en un form forjado desde otro origen); el Content-Type
 * application/json de los endpoints exige preflight, que el CORS restrictivo rechaza.
 */
@Component
public class RefreshCookieService {

    public static final String COOKIE_DUENO = "rk_refresh";
    public static final String COOKIE_CLIENTE = "rk_cliente_refresh";

    /** Path restringido a /api: la cookie solo viaja a endpoints de la API. */
    private static final String PATH = "/api";

    @Value("${app.jwt.refresh-days}")
    private long refreshDays;

    /** En dev (HTTP) debe ser false o el navegador descarta la cookie. */
    @Value("${app.cookies.secure:true}")
    private boolean secure;

    @Value("${app.cookies.same-site:Lax}")
    private String sameSite;

    public void setear(HttpServletResponse res, String nombre, String valorPlano) {
        Cookie cookie = new Cookie(nombre, valorPlano);
        cookie.setHttpOnly(true);
        cookie.setSecure(secure);
        cookie.setPath(PATH);
        cookie.setMaxAge((int) (refreshDays * 86_400));
        // Spring 6: cabecera Set-Cookie directa para SameSite (Cookie API no lo soporta).
        res.addHeader("Set-Cookie", serializar(cookie));
    }

    public void borrar(HttpServletResponse res, String nombre) {
        Cookie cookie = new Cookie(nombre, "");
        cookie.setHttpOnly(true);
        cookie.setSecure(secure);
        cookie.setPath(PATH);
        cookie.setMaxAge(0);
        res.addHeader("Set-Cookie", serializar(cookie));
    }

    private String serializar(Cookie cookie) {
        StringBuilder sb = new StringBuilder();
        sb.append(cookie.getName()).append('=').append(cookie.getValue());
        sb.append("; Path=").append(cookie.getPath());
        sb.append("; Max-Age=").append(cookie.getMaxAge());
        if (cookie.isHttpOnly()) sb.append("; HttpOnly");
        if (cookie.getSecure()) sb.append("; Secure");
        if (sameSite != null && !sameSite.isBlank()) sb.append("; SameSite=").append(sameSite);
        return sb.toString();
    }
}
