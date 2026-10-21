package pe.ventage.valuo_monitoring_service.domain.model.queries;

import java.util.List;

public record DataDriftResult(
        String operationType,
        String temporalRange,
        boolean driftDetected,
        Double shareOfDriftedFeatures,
        int driftedFeaturesCount,
        int totalFeaturesCount,
        List<FeatureDriftSummary> metrics
) {}
