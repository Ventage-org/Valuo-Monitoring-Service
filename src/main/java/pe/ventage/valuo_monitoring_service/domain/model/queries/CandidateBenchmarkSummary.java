package pe.ventage.valuo_monitoring_service.domain.model.queries;

public record CandidateBenchmarkSummary(
        CandidateModelSummary candidato,
        BenchmarkDeltas deltas,
        String recomendacion,
        String motivo
) {}
