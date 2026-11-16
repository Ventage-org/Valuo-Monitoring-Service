package pe.ventage.valuo_monitoring_service.application.internal.queryservices;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ventage.valuo_monitoring_service.application.internal.outboundservices.ExternalMlService;
import pe.ventage.valuo_monitoring_service.domain.model.aggregates.ModelTrainingJob;
import pe.ventage.valuo_monitoring_service.domain.model.queries.GetJobStatusQuery;
import pe.ventage.valuo_monitoring_service.domain.model.queries.GetModelBenchmarkQuery;
import pe.ventage.valuo_monitoring_service.domain.model.queries.GetTrainingHistoryQuery;
import pe.ventage.valuo_monitoring_service.domain.model.queries.ModelBenchmarkResult;
import pe.ventage.valuo_monitoring_service.domain.model.valueobjects.JobStatus;
import pe.ventage.valuo_monitoring_service.domain.services.GovernanceQueryService;
import pe.ventage.valuo_monitoring_service.infrastructure.persistence.jpa.repositories.ModelTrainingJobRepository;

import java.util.Optional;

@Service
@Slf4j
public class GovernanceQueryServiceImpl implements GovernanceQueryService {

    private final ModelTrainingJobRepository jobRepository;
    private final ExternalMlService externalMlService;

    public GovernanceQueryServiceImpl(
            ModelTrainingJobRepository jobRepository,
            ExternalMlService externalMlService) {
        this.jobRepository = jobRepository;
        this.externalMlService = externalMlService;
    }

    @Override
    @Transactional
    public Optional<ModelTrainingJob> handle(GetJobStatusQuery query) {
        Optional<ModelTrainingJob> jobOpt = jobRepository.findByJobId(query.jobId());
        if (jobOpt.isEmpty()) {
            return Optional.empty();
        }

        ModelTrainingJob job = jobOpt.get();
        // If the job is still active, sync synchronously within this HTTP request (no background threads)
        if (!job.getStatus().isTerminal()) {
            try {
                externalMlService.getJobStatus(job.getJobId()).ifPresent(mlStatus -> {
                    JobStatus parsed = JobStatus.fromString(mlStatus.status());
                    if (parsed == JobStatus.RUNNING) {
                        job.markAsRunning();
                    } else if (parsed == JobStatus.SUCCESS) {
                        job.complete(mlStatus.modelVersion(), mlStatus.metricsR2(), mlStatus.metricsMae(), mlStatus.metricsMape());
                    } else if (parsed == JobStatus.FAILED) {
                        job.fail(mlStatus.errorMessage());
                    }
                    jobRepository.save(job);
                });
            } catch (Exception e) {
                log.warn("Could not sync job status from ML service on-demand: {}", e.getMessage());
            }
        }

        return Optional.of(job);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ModelTrainingJob> handle(GetTrainingHistoryQuery query) {
        return jobRepository.findByOperationTypeOrderByCreatedAtDesc(
                query.operationType(),
                query.pageable()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public ModelBenchmarkResult handle(GetModelBenchmarkQuery query) {
        return externalMlService.getBenchmark(
                query.operationType().name().toLowerCase(),
                query.soloUltimo()
        );
    }

    @Override
    public java.util.Map<String, Object> getIpcConfig() {
        return externalMlService.getIpcConfig();
    }

    @Override
    public java.util.Map<String, Object> getModelHistory(pe.ventage.valuo_monitoring_service.domain.model.valueobjects.OperationType operationType) {
        return externalMlService.getModelHistory(operationType.name().toLowerCase());
    }

    @Override
    public boolean isTrainingAvailable() {
        return externalMlService.isTrainingAvailable();
    }
}
