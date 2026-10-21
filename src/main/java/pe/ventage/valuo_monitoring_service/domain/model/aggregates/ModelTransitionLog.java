package pe.ventage.valuo_monitoring_service.domain.model.aggregates;

import jakarta.persistence.*;
import lombok.Getter;
import pe.ventage.valuo_monitoring_service.domain.model.commands.PromoteModelCommand;
import pe.ventage.valuo_monitoring_service.domain.model.commands.RollbackModelCommand;
import pe.ventage.valuo_monitoring_service.domain.model.exceptions.InvalidDomainAttributeException;
import pe.ventage.valuo_monitoring_service.domain.model.valueobjects.ModelStage;
import pe.ventage.valuo_monitoring_service.domain.model.valueobjects.OperationType;
import pe.ventage.valuo_monitoring_service.domain.model.valueobjects.TransitionAction;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "model_transition_logs", schema = "monitoring")
@Getter
public class ModelTransitionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 30)
    private TransitionAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false, length = 20)
    private OperationType operationType;

    @Column(name = "previous_version", length = 50)
    private String previousVersion;

    @Column(name = "model_version", nullable = false, length = 50)
    private String modelVersion; // Nueva versión

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "performed_by", nullable = false, length = 100)
    private String performedBy;

    @Column(name = "status", length = 30)
    private String status;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_stage", length = 30)
    private ModelStage previousStage;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_stage", nullable = false, length = 30)
    private ModelStage newStage;

    @Column(name = "delta_mape_pct")
    private Double deltaMapePct;

    @Column(name = "delta_r2")
    private Double deltaR2;

    @Column(name = "delta_mae")
    private Double deltaMae;

    @Column(name = "delta_rmse")
    private Double deltaRmse;

    @Column(name = "mejora_mape")
    private Boolean mejoraMape;

    @Column(name = "metric_change_summary", length = 255)
    private String metricChangeSummary;

    protected ModelTransitionLog() {
        // Protected constructor for JPA
    }

    public static ModelTransitionLog forPromotion(
            OperationType operationType,
            String newVersion,
            String previousVersion,
            ModelStage currentStage,
            String performedBy,
            String reason,
            Double deltaMapePct,
            Double deltaR2,
            Double deltaMae,
            Double deltaRmse,
            Boolean mejoraMape
    ) {
        if (operationType == null) {
            throw new InvalidDomainAttributeException("OperationType cannot be null");
        }
        if (newVersion == null || newVersion.isBlank()) {
            throw new InvalidDomainAttributeException("Model version is required for promotion");
        }

        ModelTransitionLog log = new ModelTransitionLog();
        log.modelVersion = newVersion.trim();
        log.previousVersion = previousVersion;
        log.operationType = operationType;
        log.previousStage = currentStage != null ? currentStage : ModelStage.STAGING;
        log.newStage = ModelStage.PRODUCTION;
        log.action = TransitionAction.PROMOTION;
        log.performedBy = performedBy != null ? performedBy : "system";
        log.reason = reason;
        log.status = "SUCCESS";
        log.deltaMapePct = deltaMapePct;
        log.deltaR2 = deltaR2;
        log.deltaMae = deltaMae;
        log.deltaRmse = deltaRmse;
        log.mejoraMape = mejoraMape;
        log.metricChangeSummary = buildDeltaSummary(deltaMapePct, deltaR2, deltaMae, mejoraMape);
        log.createdAt = LocalDateTime.now();
        return log;
    }

    public static ModelTransitionLog forRollback(
            OperationType operationType,
            String targetVersion,
            String previousVersion,
            String performedBy,
            String reason,
            Double deltaMapePct,
            Double deltaR2,
            Double deltaMae,
            Double deltaRmse,
            Boolean mejoraMape
    ) {
        if (operationType == null) {
            throw new InvalidDomainAttributeException("OperationType cannot be null");
        }
        if (targetVersion == null || targetVersion.isBlank()) {
            throw new InvalidDomainAttributeException("Target version is required for rollback");
        }

        ModelTransitionLog log = new ModelTransitionLog();
        log.modelVersion = targetVersion.trim();
        log.previousVersion = previousVersion;
        log.operationType = operationType;
        log.previousStage = ModelStage.PRODUCTION;
        log.newStage = ModelStage.PRODUCTION;
        log.action = TransitionAction.ROLLBACK;
        log.performedBy = performedBy != null ? performedBy : "system";
        log.reason = reason;
        log.status = "SUCCESS";
        log.deltaMapePct = deltaMapePct;
        log.deltaR2 = deltaR2;
        log.deltaMae = deltaMae;
        log.deltaRmse = deltaRmse;
        log.mejoraMape = mejoraMape;
        log.metricChangeSummary = buildDeltaSummary(deltaMapePct, deltaR2, deltaMae, mejoraMape);
        log.createdAt = LocalDateTime.now();
        return log;
    }

    public static ModelTransitionLog forTraining(
            OperationType operationType,
            String activeVersion,
            String jobId,
            String performedBy,
            String reason
    ) {
        ModelTransitionLog log = new ModelTransitionLog();
        log.modelVersion = "Job: " + jobId;
        log.previousVersion = activeVersion;
        log.operationType = operationType;
        log.previousStage = ModelStage.PRODUCTION;
        log.newStage = ModelStage.CANDIDATE;
        log.action = TransitionAction.TRAINING;
        log.performedBy = performedBy != null ? performedBy : "system";
        log.reason = reason;
        log.status = "IN_PROGRESS";
        log.metricChangeSummary = "Reentrenamiento iniciado en background";
        log.createdAt = LocalDateTime.now();
        return log;
    }

    public static ModelTransitionLog forTrainingCompleted(
            OperationType operationType,
            String newVersion,
            String activeVersion,
            String performedBy,
            Double r2,
            Double mae,
            Double mape
    ) {
        ModelTransitionLog log = new ModelTransitionLog();
        log.modelVersion = newVersion;
        log.previousVersion = activeVersion;
        log.operationType = operationType;
        log.previousStage = ModelStage.PRODUCTION;
        log.newStage = ModelStage.CANDIDATE;
        log.action = TransitionAction.TRAINING;
        log.performedBy = performedBy != null ? performedBy : "system";
        log.reason = "Entrenamiento completado y registrado en MLflow";
        log.status = "SUCCESS";
        log.metricChangeSummary = String.format("Métricas: MAPE=%.2f%% | R²=%.4f | MAE=%.2f",
                mape != null ? mape : 0.0,
                r2 != null ? r2 : 0.0,
                mae != null ? mae : 0.0);
        log.createdAt = LocalDateTime.now();
        return log;
    }

    public static ModelTransitionLog forTrainingFailed(
            OperationType operationType,
            String jobId,
            String activeVersion,
            String performedBy,
            String errorMessage
    ) {
        ModelTransitionLog log = new ModelTransitionLog();
        log.modelVersion = "Job: " + jobId;
        log.previousVersion = activeVersion;
        log.operationType = operationType;
        log.previousStage = ModelStage.PRODUCTION;
        log.newStage = ModelStage.CANDIDATE;
        log.action = TransitionAction.TRAINING;
        log.performedBy = performedBy != null ? performedBy : "system";
        log.reason = "Entrenamiento falló: " + (errorMessage != null ? errorMessage : "Error desconocido");
        log.status = "FAILED";
        log.metricChangeSummary = "Entrenamiento no completado";
        log.createdAt = LocalDateTime.now();
        return log;
    }

    private static String buildDeltaSummary(Double deltaMape, Double deltaR2, Double deltaMae, Boolean mejoraMape) {
        if (deltaMape == null && deltaR2 == null) {
            return "Sin métricas delta registradas";
        }
        StringBuilder sb = new StringBuilder();
        if (deltaMape != null) {
            sb.append(String.format("ΔMAPE: %+.2f%%", deltaMape));
        }
        if (deltaR2 != null) {
            if (!sb.isEmpty()) sb.append(" | ");
            sb.append(String.format("ΔR²: %+.4f", deltaR2));
        }
        if (deltaMae != null) {
            if (!sb.isEmpty()) sb.append(" | ");
            sb.append(String.format("ΔMAE: %+.2f", deltaMae));
        }
        if (Boolean.TRUE.equals(mejoraMape)) {
            sb.append(" (Mejora: Sí)");
        } else if (Boolean.FALSE.equals(mejoraMape)) {
            sb.append(" (Mejora: No)");
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ModelTransitionLog that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
