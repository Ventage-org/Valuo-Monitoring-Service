package pe.ventage.valuo_monitoring_service.domain.model.queries;

public record FeatureDriftSummary(
        String featureName,
        Double driftScore,
        Double threshold,
        boolean drifted,
        String testName
) {}
