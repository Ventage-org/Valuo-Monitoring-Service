package pe.ventage.valuo_monitoring_service.domain.services;

import pe.ventage.valuo_monitoring_service.domain.model.queries.*;

public interface DashboardQueryService {
    DashboardOverviewResult handle(GetDashboardOverviewQuery query);
    ModelHealthResult handle(GetModelHealthQuery query);
    DataDriftResult handle(GetDataDriftQuery query);
    ConceptDriftResult handle(GetConceptDriftQuery query);
    java.util.Map<String, Object> getSystemResources();
    java.util.Map<String, Object> getLatencyMetrics(pe.ventage.valuo_monitoring_service.domain.model.valueobjects.OperationType operationType);
}
