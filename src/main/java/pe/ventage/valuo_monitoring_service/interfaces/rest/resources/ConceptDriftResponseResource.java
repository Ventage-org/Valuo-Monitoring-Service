package pe.ventage.valuo_monitoring_service.interfaces.rest.resources;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ConceptDriftResponseResource(
        @JsonProperty("operation_type") String operationType,
        @JsonProperty("temporal_range") String temporalRange,
        @JsonProperty("drift_detected") boolean driftDetected,
        @JsonProperty("wasserstein_distance") Double wassersteinDistance,
        @JsonProperty("psi_score") Double psiScore,
        @JsonProperty("ks_statistic") Double ksStatistic,
        @JsonProperty("interpretation") String interpretation
) {}
