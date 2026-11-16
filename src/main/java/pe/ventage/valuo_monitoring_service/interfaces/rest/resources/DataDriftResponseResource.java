package pe.ventage.valuo_monitoring_service.interfaces.rest.resources;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record DataDriftResponseResource(
        @JsonProperty("operation_type") String operationType,
        @JsonProperty("temporal_range") String temporalRange,
        @JsonProperty("drift_detected") boolean driftDetected,
        @JsonProperty("share_of_drifted_features") Double shareOfDriftedFeatures,
        @JsonProperty("drifted_features_count") int driftedFeaturesCount,
        @JsonProperty("total_features_count") int totalFeaturesCount,
        @JsonProperty("metrics") List<FeatureDriftMetricResource> metrics
) {}
