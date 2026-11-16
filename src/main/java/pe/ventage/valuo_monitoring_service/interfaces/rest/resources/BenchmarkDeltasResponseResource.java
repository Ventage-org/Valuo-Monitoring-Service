package pe.ventage.valuo_monitoring_service.interfaces.rest.resources;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BenchmarkDeltasResponseResource(
        @JsonProperty("delta_mape_pct") Double deltaMapePct,
        @JsonProperty("delta_r2") Double deltaR2,
        @JsonProperty("delta_mae") Double deltaMae,
        @JsonProperty("delta_rmse") Double deltaRmse,
        @JsonProperty("mejora_mape") Boolean mejoraMape,
        @JsonProperty("mejora_r2") Boolean mejoraR2
) {}
