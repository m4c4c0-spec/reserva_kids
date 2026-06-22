package cl.reservakids.infrastructure.config;

import org.apache.catalina.connector.Connector;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Hardening del servidor embebido (principio: minimizar superficie de ataque).
 * - Elimina la cabecera Server (Tomcat/10.x) para no revelar la tecnología.
 * - Deshabilita el método TRACE (reflejo HTTP — riesgo XST en navegadores antiguos).
 */
@Configuration
public class TomcatConfig {

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatCustomizer() {
        return factory -> factory.addConnectorCustomizers((Connector connector) -> {
            // Suprime el header Server: no revela Apache Tomcat ni su versión
            connector.setProperty("server", "ReservaKids");
            connector.setAllowTrace(false);        // TRACE deshabilitado
        });
    }
}
