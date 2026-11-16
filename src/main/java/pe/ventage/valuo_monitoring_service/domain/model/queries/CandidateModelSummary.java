package pe.ventage.valuo_monitoring_service.domain.model.queries;

public record CandidateModelSummary(
        String nombreModelo,
        String version,
        String stage,
        String runId,
        BenchmarkMetrics metricas,
        String creadoEn,
        String fuenteArtefacto
) {}
