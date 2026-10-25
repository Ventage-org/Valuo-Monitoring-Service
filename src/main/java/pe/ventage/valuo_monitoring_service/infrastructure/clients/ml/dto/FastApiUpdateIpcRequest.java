package pe.ventage.valuo_monitoring_service.infrastructure.clients.ml.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FastApiUpdateIpcRequest(
        @JsonProperty("valor") Double valor,
        @JsonProperty("periodo") String periodo,
        @JsonProperty("fuente") String fuente,
        @JsonProperty("actualizado_por") String actualizadoPor,
        @JsonProperty("nota") String nota,
        @JsonProperty("aplicar_a") String aplicarA
) {}
