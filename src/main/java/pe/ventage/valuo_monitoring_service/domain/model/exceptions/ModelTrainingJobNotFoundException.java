package pe.ventage.valuo_monitoring_service.domain.model.exceptions;

public class ModelTrainingJobNotFoundException extends RuntimeException {
    public ModelTrainingJobNotFoundException(String jobId) {
        super("Training job not found with ID: " + jobId);
    }
}
