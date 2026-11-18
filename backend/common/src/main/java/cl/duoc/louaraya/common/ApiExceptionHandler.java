package cl.duoc.louaraya.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    public record Error(String message, Map<String, String> fieldErrors) {}

    @ExceptionHandler(ApiFailure.class)
    ResponseEntity<Error> handle(ApiFailure failure) {
        return ResponseEntity.status(failure.status()).body(new Error(failure.getMessage(), failure.fieldErrors()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Error> validation(MethodArgumentNotValidException failure) {
        Map<String, String> fields = new LinkedHashMap<>();
        failure.getFieldErrors().forEach(e -> fields.putIfAbsent(e.getField(), e.getDefaultMessage()));
        return ResponseEntity.badRequest().body(new Error("Revisa los campos indicados.", fields));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<Error> malformedRequest(Exception ignored) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new Error("La solicitud no es válida.", Map.of()));
    }
}
