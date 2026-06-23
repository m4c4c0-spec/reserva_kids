package cl.reservakids.domain.model;

/**
 * Validador de RUT chileno usando el algoritmo de Módulo 11.
 * <p>
 * Un RUT chileno tiene formato KKKK.KKK-K donde K son dígitos y el último
 * carácter es el dígito verificador (0-9 o K). El algoritmo:
 * <ol>
 *   <li>Se toma el número base (sin dígito verificador ni puntos ni guión)</li>
 *   <li>Se invierten los dígitos</li>
 *   <li>Se multiplica cada dígito por 2, 3, 4, 5, 6, 7, 2, 3... (secuencia 2-7)</li>
 *   <li>Se suman los productos</li>
 *   <li>DV = 11 - (suma % 11)</li>
 *   <li>Si DV = 11 → 0; si DV = 10 → K; sino el dígito mismo</li>
 * </ol>
 */
public final class RutValidator {

    private RutValidator() {}

    /**
     * Normaliza un RUT: elimina puntos, guiones, espacios y pasa la K a mayúscula.
     * Retorna "12.345.678-5" → "12345678-5", "76.543.210-k" → "76543210-K".
     */
    public static String normalizar(String rut) {
        if (rut == null) return null;
        String limpio = rut.replaceAll("[^0-9kK]", "").toUpperCase();
        if (limpio.length() < 2) return limpio;
        String numero = limpio.substring(0, limpio.length() - 1);
        String dv = limpio.substring(limpio.length() - 1);
        return numero + "-" + dv;
    }

    /** Formatea un RUT para mostrar: "12345678-5" → "12.345.678-5". */
    public static String formatear(String rutNormalizado) {
        if (rutNormalizado == null) return null;
        int guion = rutNormalizado.indexOf('-');
        if (guion < 0) return rutNormalizado;
        String numero = rutNormalizado.substring(0, guion);
        String dv = rutNormalizado.substring(guion);
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (int i = numero.length() - 1; i >= 0; i--) {
            sb.append(numero.charAt(i));
            count++;
            if (count % 3 == 0 && i > 0) sb.append('.');
        }
        return sb.reverse().toString() + dv;
    }

    /** Valida un RUT contra el algoritmo de Módulo 11. Acepta con/sin puntos ni guiones. */
    public static boolean esValido(String rut) {
        if (rut == null || rut.isBlank()) return false;
        String limpio = rut.replaceAll("[^0-9kK]", "").toUpperCase();
        if (limpio.length() < 2) return false;

        String dv = limpio.substring(limpio.length() - 1);
        String numero = limpio.substring(0, limpio.length() - 1);

        if (!numero.matches("\\d+")) return false;
        if (!dv.matches("[0-9K]")) return false;

        return dv.equals(calcularDv(numero));
    }

    /** Calcula el dígito verificador (0-9 o K) para un número de RUT dado. */
    public static String calcularDv(String numero) {
        int suma = 0;
        int factor = 2;
        // Módulo 11 chileno: se recorre de DERECHA a IZQUIERDA con secuencia 2,3,4,5,6,7,2,3...
        for (int i = numero.length() - 1; i >= 0; i--) {
            suma += (numero.charAt(i) - '0') * factor;
            factor = factor == 7 ? 2 : factor + 1;
        }
        int dv = 11 - (suma % 11);
        if (dv == 11) return "0";
        if (dv == 10) return "K";
        return String.valueOf(dv);
    }
}
