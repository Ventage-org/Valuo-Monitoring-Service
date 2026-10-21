package pe.ventage.valuo_monitoring_service.domain.model.queries;

import java.util.List;

public record DashboardOverviewResult(
        ModelHealthResult health,
        DataDriftResult dataDrift,
        ConceptDriftResult conceptDrift,
        String globalStatus,
        List<String> activeAlerts
) {}
