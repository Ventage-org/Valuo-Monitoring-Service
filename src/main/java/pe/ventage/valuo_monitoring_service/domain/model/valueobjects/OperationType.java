package pe.ventage.valuo_monitoring_service.domain.model.valueobjects;

import pe.ventage.valuo_monitoring_service.domain.model.exceptions.InvalidDomainAttributeException;

public enum OperationType {
    VENTA,
    ALQUILER;

    public static OperationType fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidDomainAttributeException("OperationType cannot be empty");
        }
        try {
            return OperationType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidDomainAttributeException("Invalid OperationType: " + value + ". Allowed values: VENTA, ALQUILER");
        }
    }
}
