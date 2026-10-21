package pe.ventage.valuo_monitoring_service.infrastructure.clients.ml.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public record FastApiJobStatusResponse(
        @JsonProperty("job_id") String jobId,
        @JsonProperty("tipo_operacion") String tipoOperacion,
        @JsonProperty("estado") String estado,
        @JsonProperty("nombre_modelo") String nombreModelo,
        @JsonProperty("iniciado_en") String iniciadoEn,
        @JsonProperty("completado_en") String completadoEn,
        @JsonProperty("duracion_segundos") Double duracionSegundos,
        @JsonProperty("progreso") String progreso,
        @JsonProperty("metricas") Map<String, Object> metricas,
        @JsonProperty("mlflow_run_id") String mlflowRunId,
        @JsonProperty("mlflow_model_version") String mlflowModelVersion,
        @JsonProperty("estado_gobernanza") String estadoGobernanza,
        @JsonProperty("error") String error
) {}
