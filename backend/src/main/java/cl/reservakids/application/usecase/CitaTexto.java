package cl.reservakids.application.usecase;

import cl.reservakids.domain.model.Reserva;
import cl.reservakids.domain.model.ReservaServicio;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Formateo común del texto de una cita para las notificaciones (email + WhatsApp). */
public final class CitaTexto {

    private static final Locale ES_CL = Locale.forLanguageTag("es-CL");
    private static final DateTimeFormatter FECHA =
            DateTimeFormatter.ofPattern("EEEE d 'de' MMMM, HH:mm", ES_CL);

    private CitaTexto() {}

    /** "viernes 19 de junio, 10:00" — usa el offset guardado en la cita (hora del negocio). */
    public static String fechaHora(Reserva cita) {
        return cita.getInicio().format(FECHA);
    }

    public static String duracion(int min) {
        int h = min / 60;
        int m = min % 60;
        StringBuilder sb = new StringBuilder();
        if (h > 0) sb.append(h).append(" h");
        if (m > 0) sb.append(sb.isEmpty() ? "" : " ").append(m).append(" min");
        return sb.isEmpty() ? "0 min" : sb.toString();
    }

    /** Líneas tipo "• Show de Magia (1 h) — $12.000". */
    public static String listaServicios(List<ReservaServicio> servicios) {
        StringBuilder sb = new StringBuilder();
        for (ReservaServicio s : servicios) {
            sb.append("• ").append(s.getNombre())
                    .append(" (").append(duracion(s.getDuracionMin())).append(") — ")
                    .append(clp(s.getPrecioClp())).append('\n');
        }
        return sb.toString();
    }

    public static int total(List<ReservaServicio> servicios) {
        return servicios.stream().mapToInt(ReservaServicio::getPrecioClp).sum();
    }

    /** "$12.345" (CLP sin decimales, separador de miles chileno). */
    public static String clp(int monto) {
        return "$" + String.format(Locale.forLanguageTag("es-CL"), "%,d", monto);
    }
}
