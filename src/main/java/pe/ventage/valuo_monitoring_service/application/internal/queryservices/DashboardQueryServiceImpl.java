package pe.ventage.valuo_monitoring_service.application.internal.queryservices;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.ventage.valuo_monitoring_service.application.internal.outboundservices.ExternalMlService;
import pe.ventage.valuo_monitoring_service.application.internal.outboundservices.ExternalMlService.*;
import pe.ventage.valuo_monitoring_service.domain.model.aggregates.TemporalDriftSnapshot;
import pe.ventage.valuo_monitoring_service.domain.model.queries.*;
import pe.ventage.valuo_monitoring_service.domain.services.DashboardQueryService;
import pe.ventage.valuo_monitoring_service.infrastructure.persistence.jpa.repositories.TemporalDriftSnapshotRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@Transactional(readOnly = true)
public class DashboardQueryServiceImpl implements DashboardQueryService {

    private final ExternalMlService externalMlService;
    private final TemporalDriftSnapshotRepository snapshotRepository;
    private final ObjectMapper objectMapper;

    public DashboardQueryServiceImpl(
            ExternalMlService externalMlService,
            TemporalDriftSnapshotRepository snapshotRepository,
            ObjectMapper objectMapper) {
        this.externalMlService = externalMlService;
        this.snapshotRepository = snapshotRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public DashboardOverviewResult handle(GetDashboardOverviewQuery query) {
        ModelHealthResult health = handle(new GetModelHealthQuery(query.operationType()));
        DataDriftResult dataDrift = handle(new GetDataDriftQuery(query.operationType(), "30d"));
        ConceptDriftResult conceptDrift = handle(new GetConceptDriftQuery(query.operationType(), "30d"));

        List<String> alerts = new ArrayList<>();
        if (dataDrift.driftDetected()) {
            alerts.add("Data Drift detectado en " + dataDrift.driftedFeaturesCount() + " variables");
        }
        if (conceptDrift.driftDetected()) {
            alerts.add("Concept Drift crítico detectado (PSI=" + conceptDrift.psiScore() + ")");
        }
        if ("DEGRADED".equalsIgnoreCase(health.status()) || "CRITICAL".equalsIgnoreCase(health.status())) {
            alerts.add("Rendimiento del modelo degradado (R2=" + health.r2() + ")");
        }

        String globalStatus = "HEALTHY";
        if (conceptDrift.driftDetected() || "CRITICAL".equalsIgnoreCase(health.status())) {
            globalStatus = "CRITICAL";
        } else if (dataDrift.driftDetected() || "DEGRADED".equalsIgnoreCase(health.status())) {
            globalStatus = "WARNING";
        }

        return new DashboardOverviewResult(health, dataDrift, conceptDrift, globalStatus, alerts);
    }

    @Override
    public ModelHealthResult handle(GetModelHealthQuery query) {
        MlHealthMetricsResponse res = externalMlService.getHealthMetrics(query.operationType().name());
        return new ModelHealthResult(
                res.operationType(),
                res.activeModelVersion(),
                res.r2(),
                res.mae(),
                res.mape(),
                res.status(),
                LocalDateTime.now()
        );
    }

    @Override
    public DataDriftResult handle(GetDataDriftQuery query) {
        String range = (query.temporalRange() != null && !query.temporalRange().isBlank())
                ? query.temporalRange() : "30d";

        Optional<TemporalDriftSnapshot> snapshotOpt = snapshotRepository
                .findTopByOperationTypeAndTemporalRangeOrderBySnapshotDateDesc(query.operationType(), range);

        if (snapshotOpt.isPresent()) {
            TemporalDriftSnapshot s = snapshotOpt.get();
            List<FeatureDriftSummary> metrics = parseFeatureDriftMetrics(s.getTopDriftedFeatures());
            return new DataDriftResult(
                    s.getOperationType().name(),
                    s.getTemporalRange(),
                    s.getTargetDriftDetected() != null && s.getTargetDriftDetected(),
                    metrics.isEmpty() ? 0.0 : (double) s.getFeatureDriftCount() / metrics.size(),
                    s.getFeatureDriftCount() != null ? s.getFeatureDriftCount() : 0,
                    metrics.size(),
                    metrics
            );
        }

        MlDataDriftDetailResponse remote = externalMlService.getDataDrift(query.operationType().name(), range);
        return new DataDriftResult(
                remote.operationType(),
                remote.temporalRange(),
                remote.driftDetected(),
                remote.shareOfDriftedFeatures(),
                remote.driftedFeaturesCount(),
                remote.totalFeaturesCount(),
                remote.metrics()
        );
    }

    @Override
    public ConceptDriftResult handle(GetConceptDriftQuery query) {
        String range = (query.temporalRange() != null && !query.temporalRange().isBlank())
                ? query.temporalRange() : "30d";

        Optional<TemporalDriftSnapshot> snapshotOpt = snapshotRepository
                .findTopByOperationTypeAndTemporalRangeOrderBySnapshotDateDesc(query.operationType(), range);

        if (snapshotOpt.isPresent()) {
            TemporalDriftSnapshot s = snapshotOpt.get();
            boolean detected = s.getTargetDriftDetected() != null && s.getTargetDriftDetected();
            String interp = detected ? "Deriva de concepto detectada" : "Estable";
            return new ConceptDriftResult(
                    s.getOperationType().name(),
                    s.getTemporalRange(),
                    detected,
                    s.getWassersteinDistance(),
                    s.getPsiScore(),
                    null,
                    interp
            );
        }

        MlConceptDriftDetailResponse remote = externalMlService.getConceptDrift(query.operationType().name(), range);
        return new ConceptDriftResult(
                remote.operationType(),
                remote.temporalRange(),
                remote.driftDetected(),
                remote.wassersteinDistance(),
                remote.psiScore(),
                remote.ksStatistic(),
                remote.interpretation()
        );
    }

    private List<FeatureDriftSummary> parseFeatureDriftMetrics(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to parse drift metrics JSON from snapshot: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public java.util.Map<String, Object> getSystemResources() {
        return externalMlService.getSystemResources();
    }

    @Override
    public java.util.Map<String, Object> getLatencyMetrics(pe.ventage.valuo_monitoring_service.domain.model.valueobjects.OperationType operationType) {
        return externalMlService.getLatencyMetrics(operationType.name().toLowerCase());
    }
}
