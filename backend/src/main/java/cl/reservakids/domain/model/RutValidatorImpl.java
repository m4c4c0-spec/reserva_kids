package cl.reservakids.domain.model;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class RutValidatorImpl implements ConstraintValidator<Rut, String> {

    @Override
    public boolean isValid(String rut, ConstraintValidatorContext context) {
        if (rut == null || rut.isBlank()) return true; // @NotBlank aparte
        return RutValidator.esValido(rut);
    }
}
