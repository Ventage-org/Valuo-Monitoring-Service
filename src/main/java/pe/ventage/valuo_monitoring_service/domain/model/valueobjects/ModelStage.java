package pe.ventage.valuo_monitoring_service.domain.model.valueobjects;

import pe.ventage.valuo_monitoring_service.domain.model.exceptions.InvalidDomainAttributeException;

public enum ModelStage {
    DEVELOPMENT,
    STAGING,
    CANDIDATE,
    PRODUCTION,
    ARCHIVED;

    public static ModelStage fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidDomainAttributeException("ModelStage cannot be empty");
        }
        try {
            return ModelStage.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidDomainAttributeException("Invalid ModelStage: " + value);
        }
    }
}
