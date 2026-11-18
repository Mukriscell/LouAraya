package cl.duoc.louaraya.common;

import org.springframework.http.HttpStatus;

import java.util.Map;

public final class ApiFailure extends RuntimeException {
    private final HttpStatus status;
    private final Map<String, String> fieldErrors;

    public ApiFailure(HttpStatus status, String message) {
        this(status, message, Map.of());
    }

    private ApiFailure(HttpStatus status, String message, Map<String, String> fieldErrors) {
        super(message);
        this.status = status;
        this.fieldErrors = fieldErrors;
    }

    public HttpStatus status() {
        return status;
    }

    public Map<String, String> fieldErrors() {
        return fieldErrors;
    }

    public static ApiFailure badRequest(String message) {
        return new ApiFailure(HttpStatus.BAD_REQUEST, message);
    }

    public static ApiFailure field(String name, String message) {
        return new ApiFailure(HttpStatus.BAD_REQUEST, message, Map.of(name, message));
    }

    public static ApiFailure unauthorized() {
        return new ApiFailure(HttpStatus.UNAUTHORIZED, "Inicia sesión para continuar.");
    }

    public static ApiFailure forbidden() {
        return new ApiFailure(HttpStatus.FORBIDDEN, "Tu perfil no tiene permiso para esta operación.");
    }

    public static ApiFailure notFound(String message) {
        return new ApiFailure(HttpStatus.NOT_FOUND, message);
    }

    public static ApiFailure conflict(String message) {
        return new ApiFailure(HttpStatus.CONFLICT, message);
    }
}
