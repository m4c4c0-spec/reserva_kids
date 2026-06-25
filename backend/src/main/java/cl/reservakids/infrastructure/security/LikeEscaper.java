package cl.reservakids.infrastructure.security;

/**
 * Escapa wildcards SQL de LIKE (% y _) antes de envolver el termino de busqueda
 * en %. Previene LIKE wildcard injection: un atacante que envia "%%%%%%%%" no
 * fuerza un match masivo, y "_" no matchea cualquier caracter individual.
 */
public final class LikeEscaper {

    private LikeEscaper() {}

    /**
     * Escapa los wildcards {@code %} y {@code _} del texto y lo envuelve en %.
     * Si el texto es nulo o vacio, retorna el termino vacio sin wildcards.
     */
    public static String escaparYEnvolver(String texto) {
        if (texto == null || texto.isBlank()) return texto;
        String escapado = texto.trim()
                .toLowerCase()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escapado + "%";
    }
}
