package cl.reservakids.infrastructure.security;

import cl.reservakids.application.usecase.HtmlSanitizerPort;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

@Component
public class HtmlSanitizer implements HtmlSanitizerPort {

    private static final Safelist STRIP_ALL = Safelist.none();

    public String sanitize(String text) {
        if (text == null) return null;
        return Jsoup.clean(text, STRIP_ALL);
    }
}
