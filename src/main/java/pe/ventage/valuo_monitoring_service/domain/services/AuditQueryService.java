package pe.ventage.valuo_monitoring_service.domain.services;

import org.springframework.data.domain.Page;
import pe.ventage.valuo_monitoring_service.domain.model.aggregates.ModelTransitionLog;
import pe.ventage.valuo_monitoring_service.domain.model.queries.GetAuditLogsQuery;

public interface AuditQueryService {
    Page<ModelTransitionLog> handle(GetAuditLogsQuery query);
}
