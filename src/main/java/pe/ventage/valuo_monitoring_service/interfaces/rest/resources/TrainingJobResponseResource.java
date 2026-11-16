package pe.ventage.valuo_monitoring_service.interfaces.rest.resources;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public record TrainingJobResponseResource(
        @JsonProperty("job_id") String jobId,
        @JsonProperty("operation_type") String operationType,
        @JsonProperty("status") String status,
        @JsonProperty("dataset_version") String datasetVersion,
        @JsonProperty("target_metric") String targetMetric,
        @JsonProperty("model_version") String modelVersion,
        @JsonProperty("metrics_r2") Double metricsR2,
        @JsonProperty("metrics_mae") Double metricsMae,
        @JsonProperty("metrics_mape") Double metricsMape,
        @JsonProperty("error_message") String errorMessage,
        @JsonProperty("triggered_by") String triggeredBy,
        @JsonProperty("duration_seconds") Integer durationSeconds,
        @JsonProperty("created_at") LocalDateTime createdAt,
        @JsonProperty("completed_at") LocalDateTime completedAt
) {}
