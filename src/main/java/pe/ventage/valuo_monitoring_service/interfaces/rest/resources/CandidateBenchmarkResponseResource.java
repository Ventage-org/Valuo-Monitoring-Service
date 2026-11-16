package pe.ventage.valuo_monitoring_service.interfaces.rest.resources;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CandidateBenchmarkResponseResource(
        @JsonProperty("candidato") CandidateModelSummaryResponseResource candidato,
        @JsonProperty("deltas") BenchmarkDeltasResponseResource deltas,
        @JsonProperty("recomendacion") String recomendacion,
        @JsonProperty("motivo") String motivo
) {}
