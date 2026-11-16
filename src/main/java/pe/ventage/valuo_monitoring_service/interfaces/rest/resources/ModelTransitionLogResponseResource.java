package pe.ventage.valuo_monitoring_service.interfaces.rest.resources;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public record ModelTransitionLogResponseResource(
        @JsonProperty("id") Long id,
        @JsonProperty("created_at") LocalDateTime createdAt,
        @JsonProperty("action") String action,
        @JsonProperty("operation_type") String operationType,
        @JsonProperty("previous_version") String previousVersion,
        @JsonProperty("new_version") String newVersion,
        @JsonProperty("model_version") String modelVersion,
        @JsonProperty("reason") String reason,
        @JsonProperty("performed_by") String performedBy,
        @JsonProperty("status") String status,
        @JsonProperty("cambio_metrica") String cambioMetrica,
        @JsonProperty("deltas") BenchmarkDeltasResponseResource deltas,
        @JsonProperty("previous_stage") String previousStage,
        @JsonProperty("new_stage") String newStage
) {}
