package cl.reservakids.domain.repository;

import java.time.OffsetDateTime;

/**
 * Proyección de Spring Data para el listado de negocios de la consola de admin (F2):
 * datos del tenant + nº de reservas, resueltos en un único query agregado.
 */
public interface NegocioAdminView {
    Long getId();
    String getSlug();
    String getNombre();
    String getPlan();
    String getEstado();
    OffsetDateTime getCreadoEn();
    long getReservas();
}
