package pe.ventage.valuo_monitoring_service.interfaces.rest.resources;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ModelBenchmarkResponseResource(
        @JsonProperty("tipo_operacion") String tipoOperacion,
        @JsonProperty("benchmark_referencial_mape") Double benchmarkReferencialMape,
        @JsonProperty("modelo_produccion") ProductionModelSummaryResponseResource modeloProduccion,
        @JsonProperty("candidatos") List<CandidateBenchmarkResponseResource> candidatos,
        @JsonProperty("total_candidatos") Integer totalCandidatos,
        @JsonProperty("generado_en") String generadoEn
) {}
