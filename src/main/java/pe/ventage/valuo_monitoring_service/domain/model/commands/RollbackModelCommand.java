package pe.ventage.valuo_monitoring_service.domain.model.commands;

import pe.ventage.valuo_monitoring_service.domain.model.valueobjects.OperationType;

public record RollbackModelCommand(
        OperationType operationType,
        String targetVersion,
        String reason,
        String rolledBackBy
) {}
