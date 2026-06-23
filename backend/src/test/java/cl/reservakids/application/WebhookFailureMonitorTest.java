package cl.reservakids.application;

import cl.reservakids.application.usecase.AlertaOperacionesPort;
import cl.reservakids.application.usecase.WebhookFailureMonitor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Alertas proactivas: el monitor debe avisar a Operaciones cuando el webhook de pagos
 * acumula 3 fallos seguidos, y reiniciarse con el primer éxito.
 */
@ExtendWith(MockitoExtension.class)
class WebhookFailureMonitorTest {

    @Mock AlertaOperacionesPort alertas;

    WebhookFailureMonitor monitor;

    private static final String CANAL = "Mercado Pago";

    @BeforeEach
    void setUp() {
        monitor = new WebhookFailureMonitor(alertas);
        ReflectionTestUtils.setField(monitor, "umbral", 3);
    }

    @Test
    void noAlertaAntesDelUmbral() {
        monitor.registrarFallo(CANAL, "timeout");
        monitor.registrarFallo(CANAL, "timeout");

        verify(alertas, never()).alertaCritica(anyString(), anyString());
        assertEquals(2, monitor.fallosConsecutivos(CANAL));
    }

    @Test
    void alertaAlTercerFalloConsecutivo() {
        monitor.registrarFallo(CANAL, "timeout");
        monitor.registrarFallo(CANAL, "timeout");
        monitor.registrarFallo(CANAL, "503 Service Unavailable");

        verify(alertas, times(1)).alertaCritica(
                eq("🚨 Alerta Crítica en Pagos"),
                contains("503 Service Unavailable"));
    }

    @Test
    void unExitoReiniciaLaRacha() {
        monitor.registrarFallo(CANAL, "timeout");
        monitor.registrarFallo(CANAL, "timeout");
        monitor.registrarExito(CANAL);

        assertEquals(0, monitor.fallosConsecutivos(CANAL));

        // Tras el éxito hacen falta 3 fallos nuevos para volver a alertar.
        monitor.registrarFallo(CANAL, "timeout");
        monitor.registrarFallo(CANAL, "timeout");
        verify(alertas, never()).alertaCritica(anyString(), anyString());

        monitor.registrarFallo(CANAL, "timeout");
        verify(alertas, times(1)).alertaCritica(anyString(), anyString());
    }

    @Test
    void reAlertaCadaUmbralDeFallos() {
        // 6 fallos seguidos → alerta en el 3.º y en el 6.º (no en cada uno).
        for (int i = 0; i < 6; i++) {
            monitor.registrarFallo(CANAL, "down");
        }
        verify(alertas, times(2)).alertaCritica(anyString(), anyString());
    }
}
