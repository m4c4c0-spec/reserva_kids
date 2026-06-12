package cl.reservakids.infrastructure.web;

import cl.reservakids.domain.exception.ConflictoBloqueException;
import cl.reservakids.domain.exception.RecursoNoEncontradoException;
import cl.reservakids.domain.exception.TransicionInvalidaException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.stream.Collectors;

/** Errores uniformes (SDLC §6): {timestamp, status, error, message}. */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    public record ErrorBody(String timestamp, int status, String error, String message) {
        static ErrorBody de(HttpStatus status, String message) {
            return new ErrorBody(OffsetDateTime.now().toString(), status.value(),
                    status.getReasonPhrase(), message);
        }
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ErrorBody> noEncontrado(RecursoNoEncontradoException e) {
        return body(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler({ConflictoBloqueException.class, TransicionInvalidaException.class})
    public ResponseEntity<ErrorBody> conflicto(RuntimeException e) {
        return body(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorBody> peticionInvalida(IllegalArgumentException e) {
        return body(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorBody> validacion(MethodArgumentNotValidException e) {
        String detalle = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return body(HttpStatus.BAD_REQUEST, detalle);
    }

    /**
     * Parámetros malformados (?mes=2026-13, ?estado=FOO, /reservas/abc) llegaban como 500
     * con stacktrace en el log — en el endpoint público cualquier bot los gatilla a diario.
     */
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, DateTimeParseException.class,
            HttpMessageNotReadableException.class})
    public ResponseEntity<ErrorBody> parametroInvalido(Exception e) {
        return body(HttpStatus.BAD_REQUEST, "Parámetro o cuerpo de la petición inválido");
    }

    /**
     * Fix #5 (revisión de código): toda carrera TOCTOU contra un constraint (dos registros
     * simultáneos con el mismo slug/email, eliminar un bloque que una solicitud pública acaba
     * de tomar) terminaba en 500 con stacktrace. Los catch específicos siguen ganando.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorBody> integridad(DataIntegrityViolationException e) {
        return body(HttpStatus.CONFLICT, "Conflicto con un registro existente");
    }

    /** Fix #3 (revisión de código): el perdedor de una carrera con @Version recibe 409, no 500. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorBody> bloqueoOptimista(OptimisticLockingFailureException e) {
        return body(HttpStatus.CONFLICT, "La reserva fue modificada por otra operación; recarga e intenta de nuevo");
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorBody> credenciales(BadCredentialsException e) {
        return body(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorBody> generico(Exception e) {
        log.error("Error no controlado", e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor");
    }

    private ResponseEntity<ErrorBody> body(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ErrorBody.de(status, message));
    }
}
