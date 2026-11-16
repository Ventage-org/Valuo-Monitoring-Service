package pe.ventage.valuo_monitoring_service.domain.model.queries;

import java.time.LocalDateTime;

public record ModelHealthResult(
        String operationType,
        String activeModelVersion,
        Double r2,
        Double mae,
        Double mape,
        String status,
        LocalDateTime lastEvaluated
) {}
