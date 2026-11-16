package pe.ventage.valuo_monitoring_service.domain.model.queries;

import java.util.List;

public record ModelBenchmarkResult(
        String tipoOperacion,
        Double benchmarkReferencialMape,
        ProductionModelSummary modeloProduccion,
        List<CandidateBenchmarkSummary> candidatos,
        Integer totalCandidatos,
        String generadoEn
) {}
