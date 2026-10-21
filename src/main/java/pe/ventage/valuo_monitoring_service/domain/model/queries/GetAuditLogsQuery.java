package pe.ventage.valuo_monitoring_service.domain.model.queries;

import org.springframework.data.domain.Pageable;
import pe.ventage.valuo_monitoring_service.domain.model.valueobjects.OperationType;

public record GetAuditLogsQuery(
        OperationType operationType,
        Pageable pageable
) {}
