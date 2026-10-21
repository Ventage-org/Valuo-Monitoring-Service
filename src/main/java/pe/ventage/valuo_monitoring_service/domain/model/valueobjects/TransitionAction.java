package pe.ventage.valuo_monitoring_service.domain.model.valueobjects;

import pe.ventage.valuo_monitoring_service.domain.model.exceptions.InvalidDomainAttributeException;

public enum TransitionAction {
    PROMOTION,
    ROLLBACK,
    TRAINING,
    ARCHIVAL,
    CREATION;

    public static TransitionAction fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidDomainAttributeException("TransitionAction cannot be empty");
        }
        try {
            return TransitionAction.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidDomainAttributeException("Invalid TransitionAction: " + value);
        }
    }
}
