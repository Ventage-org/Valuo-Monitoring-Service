package pe.ventage.valuo_monitoring_service.domain.model.commands;

import pe.ventage.valuo_monitoring_service.domain.model.valueobjects.OperationType;

public record TriggerTrainingCommand(
        OperationType operationType,
        String datasetVersion,
        String targetMetric,
        String triggeredBy,
        Integer maxDepth,
        Double learningRate
) {}
