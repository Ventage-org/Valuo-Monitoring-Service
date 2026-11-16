package pe.ventage.valuo_monitoring_service.interfaces.rest.resources;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ProductionModelSummaryResponseResource(
        @JsonProperty("nombre_modelo") String nombreModelo,
        @JsonProperty("version") String version,
        @JsonProperty("stage") String stage,
        @JsonProperty("run_id") String runId,
        @JsonProperty("metricas") BenchmarkMetricsResponseResource metricas,
        @JsonProperty("creado_en") String creadoEn,
        @JsonProperty("fuente_artefacto") String fuenteArtefacto
) {}
