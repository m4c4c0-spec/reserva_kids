package cl.reservakids.application.dto;

import cl.reservakids.domain.model.Servicio;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class ServicioDtos {

    private ServicioDtos() {}

    public record ServicioRequest(
            @NotBlank @Size(max = 120) String nombre,
            @Size(max = 2000) String descripcion,
            @NotNull @Min(0) Integer precioClp,
            @Min(1) Integer duracionMin,
            @Min(1) Integer capacidad,
            Boolean activo) {}

    public record ServicioResponse(
            Long id, String nombre, String descripcion,
            Integer precioClp, Integer duracionMin, Integer capacidad, boolean activo) {

        public static ServicioResponse de(Servicio s) {
            return new ServicioResponse(s.getId(), s.getNombre(), s.getDescripcion(),
                    s.getPrecioClp(), s.getDuracionMin(), s.getCapacidad(), s.isActivo());
        }
    }
}
