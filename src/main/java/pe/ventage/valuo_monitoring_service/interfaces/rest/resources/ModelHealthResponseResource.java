package pe.ventage.valuo_monitoring_service.interfaces.rest.resources;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public record ModelHealthResponseResource(
        @JsonProperty("operation_type") String operationType,
        @JsonProperty("active_model_version") String activeModelVersion,
        @JsonProperty("r2") Double r2,
        @JsonProperty("mae") Double mae,
        @JsonProperty("mape") Double mape,
        @JsonProperty("status") String status,
        @JsonProperty("last_evaluated") LocalDateTime lastEvaluated
) {}
