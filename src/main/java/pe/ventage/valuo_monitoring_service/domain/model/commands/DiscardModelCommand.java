package pe.ventage.valuo_monitoring_service.domain.model.commands;

import pe.ventage.valuo_monitoring_service.domain.model.valueobjects.OperationType;

public record DiscardModelCommand(
        OperationType operationType,
        String version,
        String reason,
        String discardedBy
) {}

