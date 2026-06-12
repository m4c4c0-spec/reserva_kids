package cl.reservakids.domain;

import cl.reservakids.domain.model.Cliente;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Falla #9 (2 años) + fix #8 (revisión de código): normalización de teléfonos chilenos. */
class ClienteTest {

    @Test
    void normalizaFormatosHabitualesAlMismoNumero() {
        assertEquals("56912345678", Cliente.normalizarTelefono("+56 9 1234 5678"));
        assertEquals("56912345678", Cliente.normalizarTelefono("56912345678"));
        assertEquals("56912345678", Cliente.normalizarTelefono("912345678"));
        assertEquals("56912345678", Cliente.normalizarTelefono("9 1234 5678"));
        assertEquals("56912345678", Cliente.normalizarTelefono("0056912345678")); // fix #8: prefijo 00
    }

    @Test
    void noInventaCodigoDePaisEnNumerosAmbiguos() {
        // 8 dígitos (fijo) o formatos raros: solo se limpian, sin adivinar
        assertEquals("45221234", Cliente.normalizarTelefono("45-22-1234"));
    }

    @Test
    void nuloSigueNulo() {
        assertNull(Cliente.normalizarTelefono(null));
    }
}
