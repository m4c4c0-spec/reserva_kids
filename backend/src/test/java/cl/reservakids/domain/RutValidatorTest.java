package cl.reservakids.domain;

import cl.reservakids.domain.model.RutValidator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RutValidatorTest {

    @Test
    void rutsValidos() {
        assertTrue(RutValidator.esValido("11111111-1"));
        assertTrue(RutValidator.esValido("12345678-5"));
        assertTrue(RutValidator.esValido("76543210-3"));
        assertTrue(RutValidator.esValido("11.111.111-1"));
        assertTrue(RutValidator.esValido("12.345.678-5"));
        assertTrue(RutValidator.esValido("76.543.210-3"));
        assertTrue(RutValidator.esValido("111111111"));   // sin guion
        assertTrue(RutValidator.esValido("123456785"));
        // DV = K: 6*2=12, 12%11=1, 11-1=10 → K
        assertTrue(RutValidator.esValido("6-K"));
        assertTrue(RutValidator.esValido("6-k"));
    }

    @Test
    void rutsInvalidos() {
        assertFalse(RutValidator.esValido("12345678-0"));
        assertFalse(RutValidator.esValido("11111111-2"));
        assertFalse(RutValidator.esValido("76543210-5"));
        assertFalse(RutValidator.esValido("12.345.678-0"));
        assertFalse(RutValidator.esValido(""));
        assertFalse(RutValidator.esValido(null));
        assertFalse(RutValidator.esValido("abc"));
        assertFalse(RutValidator.esValido("0-1"));  // DV de "0" es 0
    }

    @Test
    void normalizarFormateaCorrectamente() {
        assertEquals("12345678-5", RutValidator.normalizar("12.345.678-5"));
        assertEquals("6-K", RutValidator.normalizar("6-k"));
        assertEquals("11111111-1", RutValidator.normalizar("111111111"));
    }

    @Test
    void formatearParaMostrar() {
        assertEquals("12.345.678-5", RutValidator.formatear("12345678-5"));
        assertEquals("6-K", RutValidator.formatear("6-K"));
        assertEquals("1.111.111-1", RutValidator.formatear("1111111-1"));
    }

    @Test
    void calcularDv() {
        assertEquals("1", RutValidator.calcularDv("11111111"));
        assertEquals("5", RutValidator.calcularDv("12345678"));
        assertEquals("3", RutValidator.calcularDv("76543210"));
        assertEquals("K", RutValidator.calcularDv("6"));
    }
}
