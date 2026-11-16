package pe.ventage.valuo_monitoring_service.infrastructure.persistence.jpa.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.ventage.valuo_monitoring_service.domain.model.aggregates.ModelTrainingJob;
import pe.ventage.valuo_monitoring_service.domain.model.valueobjects.JobStatus;
import pe.ventage.valuo_monitoring_service.domain.model.valueobjects.OperationType;

import java.util.List;
import java.util.Optional;

@Repository
public interface ModelTrainingJobRepository extends JpaRepository<ModelTrainingJob, Long> {

    Optional<ModelTrainingJob> findByJobId(String jobId);

    Page<ModelTrainingJob> findByOperationTypeOrderByCreatedAtDesc(OperationType operationType, Pageable pageable);

    List<ModelTrainingJob> findByStatus(JobStatus status);

    List<ModelTrainingJob> findByStatusIn(List<JobStatus> statuses);

    boolean existsByJobId(String jobId);
}
