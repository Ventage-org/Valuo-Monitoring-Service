package pe.ventage.valuo_monitoring_service.infrastructure.clients.ml.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FastApiTrainingTriggerResponse(
        @JsonProperty("job_id") String jobId,
        @JsonProperty("tipo_operacion") String tipoOperacion,
        @JsonProperty("estado") String estado,
        @JsonProperty("nombre_modelo") String nombreModelo,
        @JsonProperty("iniciado_en") String iniciadoEn,
        @JsonProperty("mensaje") String mensaje,
        @JsonProperty("consultar_estado_en") String consultarEstadoEn
) {}
