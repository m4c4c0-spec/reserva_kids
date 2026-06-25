package cl.reservakids.application.usecase;

/**
 * Puerto de aplicación para sanitización HTML (defensa XSS).
 * La capa de infraestructura implementa este puerto con jsoup.
 */
public interface HtmlSanitizerPort {

    String sanitize(String text);
}
