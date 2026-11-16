package pe.ventage.valuo_monitoring_service.interfaces.rest.resources;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FeatureDriftMetricResource(
        @JsonProperty("feature_name") String featureName,
        @JsonProperty("drift_score") Double driftScore,
        @JsonProperty("threshold") Double threshold,
        @JsonProperty("drifted") boolean drifted,
        @JsonProperty("test_name") String testName
) {}
