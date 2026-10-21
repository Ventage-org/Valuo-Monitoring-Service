package pe.ventage.valuo_monitoring_service.interfaces.rest.transform;

import pe.ventage.valuo_monitoring_service.domain.model.queries.*;
import pe.ventage.valuo_monitoring_service.interfaces.rest.resources.*;

import java.util.List;

public final class ModelBenchmarkResourceAssembler {

    private ModelBenchmarkResourceAssembler() {}

    public static ModelBenchmarkResponseResource toResource(ModelBenchmarkResult result) {
        if (result == null) return null;

        ProductionModelSummaryResponseResource prod = toResource(result.modeloProduccion());
        List<CandidateBenchmarkResponseResource> candidates = result.candidatos() == null
                ? List.of()
                : result.candidatos().stream().map(ModelBenchmarkResourceAssembler::toResource).toList();

        return new ModelBenchmarkResponseResource(
                result.tipoOperacion(),
                result.benchmarkReferencialMape(),
                prod,
                candidates,
                result.totalCandidatos(),
                result.generadoEn()
        );
    }

    public static ProductionModelSummaryResponseResource toResource(ProductionModelSummary summary) {
        if (summary == null) return null;
        return new ProductionModelSummaryResponseResource(
                summary.nombreModelo(),
                summary.version(),
                summary.stage(),
                summary.runId(),
                toResource(summary.metricas()),
                summary.creadoEn(),
                summary.fuenteArtefacto()
        );
    }

    public static CandidateBenchmarkResponseResource toResource(CandidateBenchmarkSummary summary) {
        if (summary == null) return null;
        return new CandidateBenchmarkResponseResource(
                toResource(summary.candidato()),
                toResource(summary.deltas()),
                summary.recomendacion(),
                summary.motivo()
        );
    }

    public static CandidateModelSummaryResponseResource toResource(CandidateModelSummary summary) {
        if (summary == null) return null;
        return new CandidateModelSummaryResponseResource(
                summary.nombreModelo(),
                summary.version(),
                summary.stage(),
                summary.runId(),
                toResource(summary.metricas()),
                summary.creadoEn(),
                summary.fuenteArtefacto()
        );
    }

    public static BenchmarkDeltasResponseResource toResource(BenchmarkDeltas deltas) {
        if (deltas == null) return null;
        return new BenchmarkDeltasResponseResource(
                deltas.deltaMapePct(),
                deltas.deltaR2(),
                deltas.deltaMae(),
                deltas.deltaRmse(),
                deltas.mejoraMape(),
                deltas.mejoraR2()
        );
    }

    public static BenchmarkMetricsResponseResource toResource(BenchmarkMetrics metrics) {
        if (metrics == null) return null;
        return new BenchmarkMetricsResponseResource(
                metrics.mapePct(),
                metrics.r2(),
                metrics.mae(),
                metrics.rmse(),
                metrics.nTrain(),
                metrics.nTest()
        );
    }
}
