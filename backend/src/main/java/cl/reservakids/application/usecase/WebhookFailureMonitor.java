package cl.reservakids.application.usecase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Vigila fallos consecutivos de los webhooks de pago (p. ej. la API de Mercado Pago caída).
 * Cuando un canal acumula {@code umbral} fallos seguidos, dispara una alerta crítica al equipo
 * de Operaciones — antes de que el cliente final note que su pago no se confirmó.
 *
 * <p>El contador se reinicia con el primer éxito. Para no inundar el canal, re-alerta cada
 * {@code umbral} fallos adicionales (3, 6, 9…), no en cada fallo.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookFailureMonitor {

    private final AlertaOperacionesPort alertas;

    /** Fallos consecutivos necesarios para gatillar la alerta (RNF observabilidad). */
    @Value("${app.alertas.webhook.umbral:3}")
    private int umbral;

    private final Map<String, AtomicInteger> fallosPorCanal = new ConcurrentHashMap<>();

    /**
     * Registra un fallo del webhook. Si se alcanza el umbral de fallos consecutivos,
     * emite la alerta crítica.
     *
     * @param canal        identificador del canal de pago, p. ej. "Mercado Pago".
     * @param detalleError mensaje del último error (se incluye en la alerta).
     */
    public void registrarFallo(String canal, String detalleError) {
        int consecutivos = fallosPorCanal
                .computeIfAbsent(canal, k -> new AtomicInteger())
                .incrementAndGet();

        log.warn("Webhook {} falló ({} consecutivos)", canal, consecutivos);

        if (consecutivos >= umbral && consecutivos % umbral == 0) {
            String titulo = "🚨 Alerta Crítica en Pagos";
            String detalle = "El webhook de " + canal + " falló " + consecutivos
                    + " veces seguidas. Posible caída del proveedor de pagos.\n"
                    + "Último error: " + (detalleError == null ? "—" : detalleError);
            alertas.alertaCritica(titulo, detalle);
        }
    }

    /** Registra un webhook procesado con éxito: reinicia el contador de fallos del canal. */
    public void registrarExito(String canal) {
        AtomicInteger contador = fallosPorCanal.get(canal);
        if (contador != null && contador.getAndSet(0) >= umbral) {
            log.info("Webhook {} se recuperó tras una racha de fallos", canal);
        }
    }

    /** Fallos consecutivos actuales de un canal (para tests / métricas). */
    public int fallosConsecutivos(String canal) {
        AtomicInteger contador = fallosPorCanal.get(canal);
        return contador == null ? 0 : contador.get();
    }
}
