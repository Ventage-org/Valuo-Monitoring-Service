package pe.ventage.valuo_monitoring_service.infrastructure.persistence.jpa.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.ventage.valuo_monitoring_service.domain.model.aggregates.TemporalDriftSnapshot;
import pe.ventage.valuo_monitoring_service.domain.model.valueobjects.OperationType;

import java.util.Optional;

@Repository
public interface TemporalDriftSnapshotRepository extends JpaRepository<TemporalDriftSnapshot, Long> {

    Optional<TemporalDriftSnapshot> findTopByOperationTypeAndTemporalRangeOrderBySnapshotDateDesc(
            OperationType operationType, String temporalRange);
}
