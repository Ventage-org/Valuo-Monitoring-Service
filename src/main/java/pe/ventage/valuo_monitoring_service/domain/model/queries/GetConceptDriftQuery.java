package pe.ventage.valuo_monitoring_service.domain.model.queries;

import pe.ventage.valuo_monitoring_service.domain.model.valueobjects.OperationType;

public record GetConceptDriftQuery(
        OperationType operationType,
        String temporalRange
) {}
