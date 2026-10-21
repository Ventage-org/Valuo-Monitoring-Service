package pe.ventage.valuo_monitoring_service.domain.model.exceptions;

public class InvalidDomainAttributeException extends RuntimeException {
    public InvalidDomainAttributeException(String message) {
        super(message);
    }
}
