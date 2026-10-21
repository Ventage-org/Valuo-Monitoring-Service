package pe.ventage.valuo_monitoring_service.interfaces.rest.transform;

import pe.ventage.valuo_monitoring_service.domain.model.aggregates.ModelTransitionLog;
import pe.ventage.valuo_monitoring_service.interfaces.rest.resources.BenchmarkDeltasResponseResource;
import pe.ventage.valuo_monitoring_service.interfaces.rest.resources.ModelTransitionLogResponseResource;

public final class ModelTransitionLogResourceFromEntityAssembler {

    private ModelTransitionLogResourceFromEntityAssembler() {}

    public static ModelTransitionLogResponseResource toResourceFromEntity(ModelTransitionLog log) {
        if (log == null) return null;

        BenchmarkDeltasResponseResource deltas = null;
        if (log.getDeltaMapePct() != null || log.getDeltaR2() != null) {
            deltas = new BenchmarkDeltasResponseResource(
                    log.getDeltaMapePct(),
                    log.getDeltaR2(),
                    log.getDeltaMae(),
                    log.getDeltaRmse(),
                    Boolean.TRUE.equals(log.getMejoraMape()),
                    log.getDeltaR2() != null && log.getDeltaR2() > 0
            );
        }

        String actionName = log.getAction() != null ? log.getAction().name() : "DESCONOCIDO";
        String opName = log.getOperationType() != null ? log.getOperationType().name() : "";

        return new ModelTransitionLogResponseResource(
                log.getId(),
                log.getCreatedAt(),
                actionName,
                opName,
                log.getPreviousVersion(),
                log.getModelVersion(),
                log.getModelVersion(),
                log.getReason(),
                log.getPerformedBy(),
                log.getStatus(),
                log.getMetricChangeSummary(),
                deltas,
                log.getPreviousStage() != null ? log.getPreviousStage().name() : null,
                log.getNewStage() != null ? log.getNewStage().name() : null
        );
    }
}
