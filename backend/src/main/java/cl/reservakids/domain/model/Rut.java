package cl.reservakids.domain.model;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.lang.annotation.*;

/**
 * Valida que un String sea un RUT chileno válido (algoritmo Módulo 11).
 * Acepta formatos: 12345678-5, 12.345.678-5, 123456785, 12.345.678-k.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = RutValidatorImpl.class)
@Size(min = 9, max = 15)
@Pattern(regexp = "[0-9.]+-?[0-9kK]?", message = "RUT inválido: usa formato 12.345.678-5")
@Documented
public @interface Rut {
    String message() default "RUT inválido";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
