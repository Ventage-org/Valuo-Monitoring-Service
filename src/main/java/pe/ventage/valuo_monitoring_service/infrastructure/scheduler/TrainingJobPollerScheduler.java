package pe.ventage.valuo_monitoring_service.infrastructure.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pe.ventage.valuo_monitoring_service.domain.model.aggregates.ModelTrainingJob;
import pe.ventage.valuo_monitoring_service.domain.model.commands.SyncJobStatusCommand;
import pe.ventage.valuo_monitoring_service.domain.model.valueobjects.JobStatus;
import pe.ventage.valuo_monitoring_service.domain.services.GovernanceCommandService;
import pe.ventage.valuo_monitoring_service.infrastructure.persistence.jpa.repositories.ModelTrainingJobRepository;

import java.util.List;

@Component
@Slf4j
public class TrainingJobPollerScheduler {

    private final ModelTrainingJobRepository jobRepository;
    private final GovernanceCommandService governanceCommandService;

    public TrainingJobPollerScheduler(
            ModelTrainingJobRepository jobRepository,
            GovernanceCommandService governanceCommandService) {
        this.jobRepository = jobRepository;
        this.governanceCommandService = governanceCommandService;
    }

    @Scheduled(fixedDelay = 5000)
    public void syncPendingJobs() {
        List<ModelTrainingJob> activeJobs = jobRepository.findByStatusIn(List.of(JobStatus.PENDING, JobStatus.RUNNING));
        if (activeJobs.isEmpty()) {
            return;
        }

        log.debug("Polling status for {} active training job(s)", activeJobs.size());
        for (ModelTrainingJob job : activeJobs) {
            try {
                ModelTrainingJob updated = governanceCommandService.handle(new SyncJobStatusCommand(job.getJobId()));
                if (updated.getStatus().isTerminal()) {
                    log.info("Training job {} transitioned to terminal status {}", job.getJobId(), updated.getStatus());
                }
            } catch (Exception e) {
                log.warn("Failed to sync training job {}: {}", job.getJobId(), e.getMessage());
            }
        }
    }
}
