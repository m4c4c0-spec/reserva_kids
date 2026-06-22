package cl.reservakids.infrastructure.security;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

/**
 * Sanitización HTML server-side (defensa en profundidad contra XSS almacenado).
 * Elimina todo HTML de strings de texto libre (comentarios, descripciones, nombres)
 * antes de persistir. Si el frontend usa v-html en el futuro o un actor malicioso
 * envía scripts directamente a la API, el dato se limpia aquí.
 * <p>
 * Usa Jsoup con Safelist.none(): elimina todo markup, dejando solo texto plano.
 */
@Component
public class HtmlSanitizer {

    private static final Safelist STRIP_ALL = Safelist.none();

    public String sanitize(String text) {
        if (text == null) return null;
        return Jsoup.clean(text, STRIP_ALL);
    }
}
