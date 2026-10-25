package pe.ventage.valuo_monitoring_service.application.internal.commandservices;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ventage.valuo_monitoring_service.application.internal.outboundservices.ExternalMlService;
import pe.ventage.valuo_monitoring_service.application.internal.outboundservices.ExternalMlService.*;
import pe.ventage.valuo_monitoring_service.domain.model.aggregates.ModelTrainingJob;
import pe.ventage.valuo_monitoring_service.domain.model.aggregates.ModelTransitionLog;
import pe.ventage.valuo_monitoring_service.domain.model.commands.PromoteModelCommand;
import pe.ventage.valuo_monitoring_service.domain.model.commands.RollbackModelCommand;
import pe.ventage.valuo_monitoring_service.domain.model.commands.SyncJobStatusCommand;
import pe.ventage.valuo_monitoring_service.domain.model.commands.TriggerTrainingCommand;
import pe.ventage.valuo_monitoring_service.domain.model.exceptions.InvalidDomainAttributeException;
import pe.ventage.valuo_monitoring_service.domain.model.exceptions.ModelTrainingJobNotFoundException;
import pe.ventage.valuo_monitoring_service.domain.model.valueobjects.JobStatus;
import pe.ventage.valuo_monitoring_service.domain.model.valueobjects.ModelStage;
import pe.ventage.valuo_monitoring_service.domain.services.GovernanceCommandService;
import pe.ventage.valuo_monitoring_service.infrastructure.persistence.jpa.repositories.ModelTrainingJobRepository;
import pe.ventage.valuo_monitoring_service.infrastructure.persistence.jpa.repositories.ModelTransitionLogRepository;

import java.util.List;

@Service
@Slf4j
public class GovernanceCommandServiceImpl implements GovernanceCommandService {

    private final ExternalMlService externalMlService;
    private final ModelTrainingJobRepository jobRepository;
    private final ModelTransitionLogRepository transitionLogRepository;

    public GovernanceCommandServiceImpl(
            ExternalMlService externalMlService,
            ModelTrainingJobRepository jobRepository,
            ModelTransitionLogRepository transitionLogRepository) {
        this.externalMlService = externalMlService;
        this.jobRepository = jobRepository;
        this.transitionLogRepository = transitionLogRepository;
    }

    @Override
    @Transactional
    public ModelTrainingJob handle(TriggerTrainingCommand command) {
        log.info("Processing TriggerTrainingCommand for operationType={}", command.operationType());

        // Regla de Negocio: No permitir dos entrenamientos simultáneos (verificación local y remota)
        List<ModelTrainingJob> activeJobs = jobRepository.findByStatusIn(List.of(JobStatus.PENDING, JobStatus.RUNNING));
        if (!activeJobs.isEmpty()) {
            throw new pe.ventage.valuo_monitoring_service.domain.model.exceptions.TrainingConflictException(
                    "Ya existe un entrenamiento en curso (Job ID: " + activeJobs.get(0).getJobId() + "). Solo se permite un job simultáneo."
            );
        }

        if (!externalMlService.isTrainingAvailable()) {
            throw new pe.ventage.valuo_monitoring_service.domain.model.exceptions.TrainingConflictException(
                    "El servicio ML se encuentra ocupado ejecutando otro entrenamiento en este momento. Solo se permite un job simultáneo."
            );
        }

        // Always false by governance policy (requires explicit promotion step)
        MlTriggerTrainingParams params = new MlTriggerTrainingParams(
                command.operationType().name().toLowerCase(),
                command.datasetVersion(),
                command.targetMetric(),
                false,
                command.maxDepth(),
                command.learningRate()
        );

        MlJobTriggerResponse mlResponse = externalMlService.triggerTraining(params);
        log.info("External ML job triggered: jobId={}", mlResponse.jobId());

        ModelTrainingJob job = new ModelTrainingJob(command, mlResponse.jobId());
        ModelTrainingJob savedJob = jobRepository.save(job);

        // Record training in audit table
        String activeVersion = externalMlService.getActiveModel(command.operationType().name().toLowerCase())
                .map(MlActiveModelInfo::modelVersion)
                .orElse("v1.0.0");

        ModelTransitionLog auditEntry = ModelTransitionLog.forTraining(
                command.operationType(),
                activeVersion,
                mlResponse.jobId(),
                command.triggeredBy(),
                "Job de reentrenamiento iniciado en background (Job: " + mlResponse.jobId() + ")"
        );
        transitionLogRepository.save(auditEntry);

        return savedJob;
    }

    @Override
    @Transactional
    public ModelTransitionLog handle(PromoteModelCommand command) {
        log.info("Processing PromoteModelCommand for operationType={}", command.operationType());

        // Retrieve current active model to record previous stage
        ModelStage currentStage = externalMlService.getActiveModel(command.operationType().name().toLowerCase())
                .map(info -> ModelStage.fromString(info.stage()))
                .orElse(ModelStage.STAGING);

        String effectiveReason = command.reason() != null && !command.reason().isBlank()
                ? command.reason()
                : "Aprobación manual del administrador tras verificación de benchmark";

        // Pass null for version to let FastAPI promote the latest candidate automatically
        MlPromoteParams params = new MlPromoteParams(
                command.operationType().name().toLowerCase(),
                null,
                ModelStage.PRODUCTION.name(),
                effectiveReason
        );

        MlPromoteResponse response = externalMlService.promoteModel(params);
        if (!response.success()) {
            throw new InvalidDomainAttributeException("Model promotion rejected by ML service: " + response.message());
        }

        String promotedVersion = response.modelVersion();
        if (promotedVersion == null || promotedVersion.isBlank()) {
            promotedVersion = externalMlService.getActiveModel(command.operationType().name().toLowerCase())
                    .map(MlActiveModelInfo::modelVersion)
                    .orElse("unknown");
        }

        ModelTransitionLog logEntry = ModelTransitionLog.forPromotion(
                command.operationType(),
                promotedVersion,
                response.previousVersion(),
                currentStage,
                command.promotedBy(),
                effectiveReason,
                response.deltaMapePct(),
                response.deltaR2(),
                response.deltaMae(),
                response.deltaRmse(),
                response.mejoraMape()
        );
        return transitionLogRepository.save(logEntry);
    }

    @Override
    @Transactional
    public ModelTransitionLog handle(RollbackModelCommand command) {
        log.info("Processing RollbackModelCommand for targetVersion={}", command.targetVersion());

        String currentVersion = externalMlService.getActiveModel(command.operationType().name().toLowerCase())
                .map(MlActiveModelInfo::modelVersion)
                .orElse("unknown");

        MlRollbackParams params = new MlRollbackParams(
                command.operationType().name().toLowerCase(),
                command.targetVersion(),
                command.reason()
        );

        MlRollbackResponse response = externalMlService.rollbackModel(params);
        if (!response.success()) {
            throw new InvalidDomainAttributeException("Model rollback rejected by ML service: " + response.message());
        }

        String prevVersion = response.previousVersion() != null ? response.previousVersion() : currentVersion;

        ModelTransitionLog logEntry = ModelTransitionLog.forRollback(
                command.operationType(),
                command.targetVersion(),
                prevVersion,
                command.rolledBackBy(),
                command.reason(),
                response.deltaMapePct(),
                response.deltaR2(),
                response.deltaMae(),
                response.deltaRmse(),
                response.mejoraMape()
        );
        return transitionLogRepository.save(logEntry);
    }

    @Override
    @Transactional
    public ModelTrainingJob handle(SyncJobStatusCommand command) {
        ModelTrainingJob job = jobRepository.findByJobId(command.jobId())
                .orElseThrow(() -> new ModelTrainingJobNotFoundException(command.jobId()));

        if (job.getStatus().isTerminal()) {
            return job;
        }

        externalMlService.getJobStatus(job.getJobId()).ifPresent(mlStatus -> {
            JobStatus parsedStatus = JobStatus.fromString(mlStatus.status());
            if (parsedStatus == JobStatus.RUNNING) {
                job.markAsRunning();
            } else if (parsedStatus == JobStatus.SUCCESS) {
                job.complete(mlStatus.modelVersion(), mlStatus.metricsR2(), mlStatus.metricsMae(), mlStatus.metricsMape());

                String activeVersion = externalMlService.getActiveModel(job.getOperationType().name().toLowerCase())
                        .map(MlActiveModelInfo::modelVersion)
                        .orElse("unknown");

                ModelTransitionLog auditCompleted = ModelTransitionLog.forTrainingCompleted(
                        job.getOperationType(),
                        mlStatus.modelVersion(),
                        activeVersion,
                        job.getTriggeredBy(),
                        mlStatus.metricsR2(),
                        mlStatus.metricsMae(),
                        mlStatus.metricsMape()
                );
                transitionLogRepository.save(auditCompleted);
            } else if (parsedStatus == JobStatus.FAILED) {
                job.fail(mlStatus.errorMessage());

                String activeVersion = externalMlService.getActiveModel(job.getOperationType().name().toLowerCase())
                        .map(MlActiveModelInfo::modelVersion)
                        .orElse("unknown");

                ModelTransitionLog auditFailed = ModelTransitionLog.forTrainingFailed(
                        job.getOperationType(),
                        job.getJobId(),
                        activeVersion,
                        job.getTriggeredBy(),
                        mlStatus.errorMessage()
                );
                transitionLogRepository.save(auditFailed);
            }
        });

        return jobRepository.save(job);
    }

    @Override
    public java.util.Map<String, Object> updateIpcConfig(Double valor, String periodo, String fuente, String actualizadoPor, String nota, String aplicarA) {
        log.info("Updating IPC factor to {} ({}) applied to {}", valor, periodo, aplicarA);
        return externalMlService.updateIpcConfig(valor, periodo, fuente, actualizadoPor, nota, aplicarA);
    }
}
