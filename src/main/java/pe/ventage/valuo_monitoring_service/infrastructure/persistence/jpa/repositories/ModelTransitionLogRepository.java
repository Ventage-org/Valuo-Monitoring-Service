package pe.ventage.valuo_monitoring_service.infrastructure.persistence.jpa.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.ventage.valuo_monitoring_service.domain.model.aggregates.ModelTransitionLog;
import pe.ventage.valuo_monitoring_service.domain.model.valueobjects.OperationType;

@Repository
public interface ModelTransitionLogRepository extends JpaRepository<ModelTransitionLog, Long> {

    Page<ModelTransitionLog> findByOperationTypeOrderByCreatedAtDesc(OperationType operationType, Pageable pageable);

    Page<ModelTransitionLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
