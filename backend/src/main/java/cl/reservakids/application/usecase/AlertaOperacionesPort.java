package cl.reservakids.application.usecase;

/**
 * Puerto de salida para alertas operacionales críticas (incidentes que el equipo de
 * Operaciones debe ver antes que el cliente final). La implementación decide el canal:
 * MVP = Slack Incoming Webhook; podría ser PagerDuty/Opsgenie sin tocar los casos de uso.
 */
public interface AlertaOperacionesPort {

    /**
     * Envía una alerta crítica al canal de Operaciones.
     *
     * @param titulo  línea principal, p. ej. "🚨 Alerta Crítica en Pagos".
     * @param detalle contexto del incidente (servicio, contador de fallos, último error).
     */
    void alertaCritica(String titulo, String detalle);
}
