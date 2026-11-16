package pe.ventage.valuo_monitoring_service.domain.model.commands;

import pe.ventage.valuo_monitoring_service.domain.model.valueobjects.OperationType;

public record PromoteModelCommand(
        OperationType operationType,
        String reason,
        String promotedBy
) {}
