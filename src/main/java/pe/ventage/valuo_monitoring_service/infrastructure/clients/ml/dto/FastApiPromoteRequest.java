package pe.ventage.valuo_monitoring_service.infrastructure.clients.ml.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FastApiPromoteRequest(
        @JsonProperty("tipo_operacion") String tipoOperacion,
        @JsonProperty("version") String version,
        @JsonProperty("motivo") String motivo
) {}
