package pe.ventage.valuo_monitoring_service.interfaces.rest.transform;

import pe.ventage.valuo_monitoring_service.domain.model.queries.ConceptDriftResult;
import pe.ventage.valuo_monitoring_service.domain.model.queries.DashboardOverviewResult;
import pe.ventage.valuo_monitoring_service.domain.model.queries.DataDriftResult;
import pe.ventage.valuo_monitoring_service.domain.model.queries.ModelHealthResult;
import pe.ventage.valuo_monitoring_service.interfaces.rest.resources.*;

import java.util.List;

public final class DashboardResourceAssembler {

    private DashboardResourceAssembler() {}

    public static ModelHealthResponseResource toResource(ModelHealthResult r) {
        if (r == null) return null;
        return new ModelHealthResponseResource(
                r.operationType(),
                r.activeModelVersion(),
                r.r2(),
                r.mae(),
                r.mape(),
                r.status(),
                r.lastEvaluated()
        );
    }

    public static DataDriftResponseResource toResource(DataDriftResult r) {
        if (r == null) return null;
        List<FeatureDriftMetricResource> metrics = r.metrics() == null ? List.of() : r.metrics().stream()
                .map(m -> new FeatureDriftMetricResource(
                        m.featureName(),
                        m.driftScore(),
                        m.threshold(),
                        m.drifted(),
                        m.testName()
                )).toList();

        return new DataDriftResponseResource(
                r.operationType(),
                r.temporalRange(),
                r.driftDetected(),
                r.shareOfDriftedFeatures(),
                r.driftedFeaturesCount(),
                r.totalFeaturesCount(),
                metrics
        );
    }

    public static ConceptDriftResponseResource toResource(ConceptDriftResult r) {
        if (r == null) return null;
        return new ConceptDriftResponseResource(
                r.operationType(),
                r.temporalRange(),
                r.driftDetected(),
                r.wassersteinDistance(),
                r.psiScore(),
                r.ksStatistic(),
                r.interpretation()
        );
    }

    public static DashboardOverviewResponseResource toResource(DashboardOverviewResult r) {
        if (r == null) return null;
        return new DashboardOverviewResponseResource(
                toResource(r.health()),
                toResource(r.dataDrift()),
                toResource(r.conceptDrift()),
                r.globalStatus(),
                r.activeAlerts()
        );
    }
}
