package pe.ventage.valuo_monitoring_service.domain.model.queries;

public record ConceptDriftResult(
        String operationType,
        String temporalRange,
        boolean driftDetected,
        Double wassersteinDistance,
        Double psiScore,
        Double ksStatistic,
        String interpretation
) {}
