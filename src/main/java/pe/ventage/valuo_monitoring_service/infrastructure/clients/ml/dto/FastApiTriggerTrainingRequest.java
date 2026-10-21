package pe.ventage.valuo_monitoring_service.infrastructure.clients.ml.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FastApiTriggerTrainingRequest(
        @JsonProperty("nombre_modelo") String nombreModelo,
        @JsonProperty("guardar_como_activo") boolean guardarComoActivo,
        @JsonProperty("max_depth") Integer maxDepth,
        @JsonProperty("learning_rate") Double learningRate
) {}
