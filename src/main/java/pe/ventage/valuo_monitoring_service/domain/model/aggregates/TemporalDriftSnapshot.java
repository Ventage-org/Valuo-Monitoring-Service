package pe.ventage.valuo_monitoring_service.domain.model.aggregates;

import jakarta.persistence.*;
import lombok.Getter;
import pe.ventage.valuo_monitoring_service.domain.model.exceptions.InvalidDomainAttributeException;
import pe.ventage.valuo_monitoring_service.domain.model.valueobjects.OperationType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "temporal_drift_snapshots", schema = "monitoring")
@Getter
public class TemporalDriftSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false, length = 20)
    private OperationType operationType;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "temporal_range", nullable = false, length = 30)
    private String temporalRange;

    @Column(name = "num_samples")
    private Integer numSamples;

    @Column(name = "wasserstein_distance")
    private Double wassersteinDistance;

    @Column(name = "psi_score")
    private Double psiScore;

    @Column(name = "target_drift_detected")
    private Boolean targetDriftDetected;

    @Column(name = "feature_drift_count")
    private Integer featureDriftCount;

    @Column(name = "top_drifted_features", columnDefinition = "TEXT")
    private String topDriftedFeatures;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected TemporalDriftSnapshot() {
        // Protected constructor for JPA
    }

    public TemporalDriftSnapshot(OperationType operationType, LocalDate snapshotDate, String temporalRange,
                                 Integer numSamples, Double wassersteinDistance, Double psiScore,
                                 Boolean targetDriftDetected, Integer featureDriftCount, String topDriftedFeatures) {
        if (operationType == null) {
            throw new InvalidDomainAttributeException("OperationType is required for drift snapshot");
        }
        if (snapshotDate == null) {
            throw new InvalidDomainAttributeException("Snapshot date is required");
        }
        if (temporalRange == null || temporalRange.isBlank()) {
            throw new InvalidDomainAttributeException("Temporal range is required");
        }

        this.operationType = operationType;
        this.snapshotDate = snapshotDate;
        this.temporalRange = temporalRange.trim();
        this.numSamples = numSamples;
        this.wassersteinDistance = wassersteinDistance;
        this.psiScore = psiScore;
        this.targetDriftDetected = targetDriftDetected != null ? targetDriftDetected : false;
        this.featureDriftCount = featureDriftCount != null ? featureDriftCount : 0;
        this.topDriftedFeatures = topDriftedFeatures;
        this.createdAt = LocalDateTime.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TemporalDriftSnapshot that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
