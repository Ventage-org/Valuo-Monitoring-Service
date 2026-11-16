package pe.ventage.valuo_monitoring_service.domain.model.valueobjects;

import pe.ventage.valuo_monitoring_service.domain.model.exceptions.InvalidDomainAttributeException;

public enum JobStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    CANCELLED,
    TIMEOUT;

    public static JobStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidDomainAttributeException("JobStatus cannot be empty");
        }
        try {
            return JobStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidDomainAttributeException("Invalid JobStatus: " + value);
        }
    }

    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == CANCELLED || this == TIMEOUT;
    }
}
