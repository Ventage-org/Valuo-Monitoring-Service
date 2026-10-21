package pe.ventage.valuo_monitoring_service.interfaces.rest.errors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pe.ventage.valuo_monitoring_service.domain.model.exceptions.InvalidDomainAttributeException;
import pe.ventage.valuo_monitoring_service.domain.model.exceptions.ModelTrainingJobNotFoundException;
import pe.ventage.valuo_monitoring_service.infrastructure.clients.ml.MlServiceException;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ModelTrainingJobNotFoundException.class)
    public ProblemDetail handleNotFound(ModelTrainingJobNotFoundException ex) {
        log.warn("Entity not found: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Resource Not Found");
        problem.setType(URI.create("https://valuo.pe/errors/not-found"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler({InvalidDomainAttributeException.class, IllegalArgumentException.class})
    public ProblemDetail handleDomainViolation(RuntimeException ex) {
        log.warn("Domain invariant violation: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setTitle("Domain Invariant Violation");
        problem.setType(URI.create("https://valuo.pe/errors/unprocessable-entity"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fe.getField(), fe.getDefaultMessage());
        }
        log.warn("Validation error on fields: {}", fieldErrors);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Los datos enviados no son válidos");
        problem.setTitle("Bad Request");
        problem.setType(URI.create("https://valuo.pe/errors/bad-request"));
        problem.setProperty("errors", fieldErrors);
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(MlServiceException.class)
    public ProblemDetail handleMlService(MlServiceException ex) {
        log.error("Downstream ML Service error: status={}, message={}", ex.getStatus(), ex.getMessage());
        HttpStatus status = switch (ex.getStatus()) {
            case 400 -> HttpStatus.BAD_REQUEST;
            case 404 -> HttpStatus.NOT_FOUND;
            case 409 -> HttpStatus.CONFLICT;
            case 503 -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> (ex.getStatus() >= 500) ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY;
        };
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problem.setTitle("ML Service Response");
        problem.setType(URI.create("https://valuo.pe/errors/ml-service"));
        problem.setProperty("downstream_status", ex.getStatus());
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex) {
        log.error("Unhandled internal server error: ", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Ha ocurrido un error interno en el servidor. Por favor contacte con soporte si el error persiste."
        );
        problem.setTitle("Internal Server Error");
        problem.setType(URI.create("https://valuo.pe/errors/internal-server-error"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}
