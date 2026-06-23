package cl.reservakids.domain.repository;

import java.time.OffsetDateTime;

/** Proyección de la bitácora de admin (F5): acción + quién la hizo + cuándo. */
public interface AuditoriaView {
    OffsetDateTime getCreadoEn();
    String getAdminEmail();
    String getAccion();
    String getDetalle();
}
