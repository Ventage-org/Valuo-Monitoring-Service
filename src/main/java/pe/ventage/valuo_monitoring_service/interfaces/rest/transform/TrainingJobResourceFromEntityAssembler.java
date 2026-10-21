package pe.ventage.valuo_monitoring_service.interfaces.rest.transform;

import pe.ventage.valuo_monitoring_service.domain.model.aggregates.ModelTrainingJob;
import pe.ventage.valuo_monitoring_service.interfaces.rest.resources.TrainingJobResponseResource;

public final class TrainingJobResourceFromEntityAssembler {

    private TrainingJobResourceFromEntityAssembler() {}

    public static TrainingJobResponseResource toResourceFromEntity(ModelTrainingJob job) {
        if (job == null) return null;
        return new TrainingJobResponseResource(
                job.getJobId(),
                job.getOperationType().name(),
                job.getStatus().name(),
                job.getDatasetVersion(),
                job.getTargetMetric(),
                job.getModelVersion(),
                job.getMetricsR2(),
                job.getMetricsMae(),
                job.getMetricsMape(),
                job.getErrorMessage(),
                job.getTriggeredBy(),
                job.getDurationSeconds(),
                job.getCreatedAt(),
                job.getCompletedAt()
        );
    }
}
