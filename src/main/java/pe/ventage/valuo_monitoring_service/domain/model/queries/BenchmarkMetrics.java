package pe.ventage.valuo_monitoring_service.domain.model.queries;

public record BenchmarkMetrics(
        Double mapePct,
        Double r2,
        Double mae,
        Double rmse,
        Integer nTrain,
        Integer nTest
) {}
