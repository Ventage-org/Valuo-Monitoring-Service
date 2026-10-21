package pe.ventage.valuo_monitoring_service.domain.model.aggregates;

import jakarta.persistence.*;
import lombok.Getter;
import pe.ventage.valuo_monitoring_service.domain.model.commands.TriggerTrainingCommand;
import pe.ventage.valuo_monitoring_service.domain.model.exceptions.InvalidDomainAttributeException;
import pe.ventage.valuo_monitoring_service.domain.model.valueobjects.JobStatus;
import pe.ventage.valuo_monitoring_service.domain.model.valueobjects.OperationType;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "model_training_jobs", schema = "monitoring")
@Getter
public class ModelTrainingJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false, unique = true, length = 100)
    private String jobId;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false, length = 20)
    private OperationType operationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private JobStatus status;

    @Column(name = "dataset_version", length = 50)
    private String datasetVersion;

    @Column(name = "target_metric", length = 50)
    private String targetMetric;

    @Column(name = "model_version", length = 50)
    private String modelVersion;

    @Column(name = "metrics_r2")
    private Double metricsR2;

    @Column(name = "metrics_mae")
    private Double metricsMae;

    @Column(name = "metrics_mape")
    private Double metricsMape;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "triggered_by", length = 100)
    private String triggeredBy;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    protected ModelTrainingJob() {
        // Protected constructor for JPA
    }

    public ModelTrainingJob(TriggerTrainingCommand command, String externalJobId) {
        if (command == null) {
            throw new InvalidDomainAttributeException("TriggerTrainingCommand cannot be null");
        }
        if (command.operationType() == null) {
            throw new InvalidDomainAttributeException("OperationType is required");
        }
        if (externalJobId == null || externalJobId.isBlank()) {
            throw new InvalidDomainAttributeException("External Job ID is required");
        }

        this.jobId = externalJobId.trim();
        this.operationType = command.operationType();
        this.status = JobStatus.PENDING;
        this.datasetVersion = command.datasetVersion();
        this.targetMetric = command.targetMetric() != null ? command.targetMetric() : "r2";
        this.triggeredBy = command.triggeredBy() != null ? command.triggeredBy() : "system";
        this.createdAt = LocalDateTime.now();
    }

    public void markAsRunning() {
        if (this.status.isTerminal()) {
            throw new InvalidDomainAttributeException("Cannot transition to RUNNING from terminal state: " + this.status);
        }
        this.status = JobStatus.RUNNING;
    }

    public void complete(String modelVersion, Double metricsR2, Double metricsMae, Double metricsMape) {
        this.status = JobStatus.SUCCESS;
        this.modelVersion = modelVersion;
        this.metricsR2 = metricsR2;
        this.metricsMae = metricsMae;
        this.metricsMape = metricsMape;
        this.completedAt = LocalDateTime.now();
        calculateDuration();
    }

    public void fail(String errorMessage) {
        this.status = JobStatus.FAILED;
        this.errorMessage = errorMessage;
        this.completedAt = LocalDateTime.now();
        calculateDuration();
    }

    public void timeout() {
        this.status = JobStatus.TIMEOUT;
        this.errorMessage = "Job timed out in monitoring service";
        this.completedAt = LocalDateTime.now();
        calculateDuration();
    }

    private void calculateDuration() {
        if (this.createdAt != null && this.completedAt != null) {
            this.durationSeconds = (int) Duration.between(this.createdAt, this.completedAt).getSeconds();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ModelTrainingJob that)) return false;
        return Objects.equals(id, that.id) || (jobId != null && Objects.equals(jobId, that.jobId));
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(jobId != null ? jobId : id);
    }
}
