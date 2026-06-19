package cl.reservakids.infrastructure.adapter;

import cl.reservakids.application.usecase.CitaTexto;
import cl.reservakids.application.usecase.NotificacionWhatsappPort;
import cl.reservakids.domain.model.Cliente;
import cl.reservakids.domain.model.Reserva;
import cl.reservakids.domain.model.ReservaServicio;
import cl.reservakids.domain.model.Tenant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Adaptador de WhatsApp por defecto: NO envía a ningún proveedor todavía — formatea el mensaje
 * y lo deja en el log ("stub"). Cuando se conecte Meta Cloud API / Twilio, basta una nueva
 * implementación de {@link NotificacionWhatsappPort}; los casos de uso no cambian.
 *
 * <p>Mismo patrón que el email (NotificacionAdapter): el envío se hace AFTER_COMMIT y en otro
 * hilo (si la transacción de la cita revierte, el WhatsApp jamás sale), y un fallo solo se
 * loguea: nunca rompe el flujo. {@code app.whatsapp.enabled=true} sin proveedor real avisa.
 */
@Slf4j
@Component
public class WhatsappStubAdapter implements NotificacionWhatsappPort {

    @Value("${app.whatsapp.enabled:false}")
    private boolean habilitado;

    /** Paridad con el canal de email: si el proveedor real falla, hay una señal contable. */
    private final AtomicInteger fallosConsecutivos = new AtomicInteger();

    @Override
    public void confirmacionCita(Tenant tenant, Reserva cita, Cliente cliente, List<ReservaServicio> servicios) {
        if (cliente.isAnonimizado() || cliente.getTelefono() == null) {
            return; // sin teléfono válido no hay a quién escribir (Ley 21.719)
        }
        String mensaje = """
                ✅ ¡Tu hora quedó agendada en %s!
                📅 %s
                %s💲 Total: %s

                ¡Te esperamos! Te recordaremos antes de tu cita.""".formatted(
                tenant.getNombre(),
                CitaTexto.fechaHora(cita),
                CitaTexto.listaServicios(servicios),
                CitaTexto.clp(CitaTexto.total(servicios)));
        enviar(cliente.getTelefono(), mensaje);
    }

    private void enviar(String telefonoE164, String mensaje) {
        ejecutarTrasCommit(() -> {
            try {
                if (!habilitado) {
                    // Stub: el log es el "envío". Útil para verificar en dev (Mailpit + este log).
                    log.info("[WhatsApp STUB → +{}] (deshabilitado: solo log)\n{}", telefonoE164, mensaje);
                    return;
                }
                // app.whatsapp.enabled=true pero aún no hay proveedor conectado: avisar, no romper.
                log.warn("[WhatsApp] habilitado sin proveedor configurado — mensaje a +{} no enviado", telefonoE164);
                fallosConsecutivos.incrementAndGet();
            } catch (Exception e) {
                fallosConsecutivos.incrementAndGet();
                log.error("Fallo enviando WhatsApp a +{}: {}", telefonoE164, e.getMessage());
            }
        });
    }

    private void ejecutarTrasCommit(Runnable envio) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    CompletableFuture.runAsync(envio);
                }
            });
        } else {
            CompletableFuture.runAsync(envio);
        }
    }
}
