package pe.ventage.valuo_monitoring_service.interfaces.rest.resources;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BenchmarkMetricsResponseResource(
        @JsonProperty("mape_pct") Double mapePct,
        @JsonProperty("r2") Double r2,
        @JsonProperty("mae") Double mae,
        @JsonProperty("rmse") Double rmse,
        @JsonProperty("cod_iaao") Double codIaao,
        @JsonProperty("cov_iaao") Double covIaao,
        @JsonProperty("prd_iaao") Double prdIaao,
        @JsonProperty("prb_iaao") Double prbIaao,
        @JsonProperty("n_train") Integer nTrain,
        @JsonProperty("n_test") Integer nTest
) {}
