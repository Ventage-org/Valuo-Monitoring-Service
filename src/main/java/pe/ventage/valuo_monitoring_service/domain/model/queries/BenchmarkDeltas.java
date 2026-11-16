package pe.ventage.valuo_monitoring_service.domain.model.queries;

public record BenchmarkDeltas(
        Double deltaMapePct,
        Double deltaR2,
        Double deltaMae,
        Double deltaRmse,
        Boolean mejoraMape,
        Boolean mejoraR2
) {}
