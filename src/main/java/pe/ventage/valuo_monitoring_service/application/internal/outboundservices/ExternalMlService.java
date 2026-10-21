package pe.ventage.valuo_monitoring_service.application.internal.outboundservices;

import pe.ventage.valuo_monitoring_service.domain.model.queries.FeatureDriftSummary;
import pe.ventage.valuo_monitoring_service.domain.model.queries.ModelBenchmarkResult;

import java.util.List;
import java.util.Optional;

public interface ExternalMlService {
    MlJobTriggerResponse triggerTraining(MlTriggerTrainingParams params);
    Optional<MlJobStatusResponse> getJobStatus(String jobId);
    MlPromoteResponse promoteModel(MlPromoteParams params);
    MlRollbackResponse rollbackModel(MlRollbackParams params);
    MlHealthMetricsResponse getHealthMetrics(String operationType);
    MlDriftStatusResponse getDriftStatus(String operationType);
    MlDataDriftDetailResponse getDataDrift(String operationType, String temporalRange);
    MlConceptDriftDetailResponse getConceptDrift(String operationType, String temporalRange);
    Optional<MlActiveModelInfo> getActiveModel(String operationType);
    ModelBenchmarkResult getBenchmark(String operationType, boolean soloUltimo);

    record MlTriggerTrainingParams(String operationType, String datasetVersion, String targetMetric, boolean guardarComoActivo, Integer maxDepth, Double learningRate) {}
    record MlJobTriggerResponse(String jobId, String status, String message) {}
    record MlJobStatusResponse(String jobId, String status, String modelVersion, Double metricsR2, Double metricsMae, Double metricsMape, String errorMessage) {}
    record MlPromoteParams(String operationType, String modelVersion, String targetStage, String reason) {}
    record MlPromoteResponse(
            boolean success,
            String modelVersion,
            String previousVersion,
            String newStage,
            String message,
            Double deltaMapePct,
            Double deltaR2,
            Double deltaMae,
            Double deltaRmse,
            Boolean mejoraMape
    ) {}
    record MlRollbackParams(String operationType, String targetVersion, String reason) {}
    record MlRollbackResponse(
            boolean success,
            String targetVersion,
            String previousVersion,
            String message,
            Double deltaMapePct,
            Double deltaR2,
            Double deltaMae,
            Double deltaRmse,
            Boolean mejoraMape
    ) {}
    record MlHealthMetricsResponse(String operationType, String activeModelVersion, Double r2, Double mae, Double mape, String status) {}
    record MlDriftStatusResponse(String operationType, boolean dataDriftDetected, boolean conceptDriftDetected, Double shareOfDriftedFeatures, int driftedFeaturesCount, int totalFeaturesCount, Double wassersteinDistance, Double psiScore) {}
    record MlDataDriftDetailResponse(String operationType, String temporalRange, boolean driftDetected, Double shareOfDriftedFeatures, int driftedFeaturesCount, int totalFeaturesCount, List<FeatureDriftSummary> metrics) {}
    record MlConceptDriftDetailResponse(String operationType, String temporalRange, boolean driftDetected, Double wassersteinDistance, Double psiScore, Double ksStatistic, String interpretation) {}
    record MlActiveModelInfo(String modelVersion, String stage, String operationType) {}
}
