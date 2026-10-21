package pe.ventage.valuo_monitoring_service.domain.services;

import org.springframework.data.domain.Page;
import pe.ventage.valuo_monitoring_service.domain.model.aggregates.ModelTrainingJob;
import pe.ventage.valuo_monitoring_service.domain.model.queries.GetJobStatusQuery;
import pe.ventage.valuo_monitoring_service.domain.model.queries.GetModelBenchmarkQuery;
import pe.ventage.valuo_monitoring_service.domain.model.queries.GetTrainingHistoryQuery;
import pe.ventage.valuo_monitoring_service.domain.model.queries.ModelBenchmarkResult;

import java.util.Optional;

public interface GovernanceQueryService {
    Optional<ModelTrainingJob> handle(GetJobStatusQuery query);
    Page<ModelTrainingJob> handle(GetTrainingHistoryQuery query);
    ModelBenchmarkResult handle(GetModelBenchmarkQuery query);
}
