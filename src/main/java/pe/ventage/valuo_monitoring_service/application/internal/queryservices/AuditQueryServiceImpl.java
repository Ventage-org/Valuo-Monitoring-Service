package pe.ventage.valuo_monitoring_service.application.internal.queryservices;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ventage.valuo_monitoring_service.domain.model.aggregates.ModelTransitionLog;
import pe.ventage.valuo_monitoring_service.domain.model.queries.GetAuditLogsQuery;
import pe.ventage.valuo_monitoring_service.domain.services.AuditQueryService;
import pe.ventage.valuo_monitoring_service.infrastructure.persistence.jpa.repositories.ModelTransitionLogRepository;

@Service
@Transactional(readOnly = true)
public class AuditQueryServiceImpl implements AuditQueryService {

    private final ModelTransitionLogRepository transitionLogRepository;

    public AuditQueryServiceImpl(ModelTransitionLogRepository transitionLogRepository) {
        this.transitionLogRepository = transitionLogRepository;
    }

    @Override
    public Page<ModelTransitionLog> handle(GetAuditLogsQuery query) {
        if (query.operationType() == null) {
            return transitionLogRepository.findAllByOrderByCreatedAtDesc(query.pageable());
        }
        return transitionLogRepository.findByOperationTypeOrderByCreatedAtDesc(
                query.operationType(),
                query.pageable()
        );
    }
}
