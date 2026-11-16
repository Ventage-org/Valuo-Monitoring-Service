package pe.ventage.valuo_monitoring_service.interfaces.rest.transform;

import pe.ventage.valuo_monitoring_service.domain.model.commands.TriggerTrainingCommand;
import pe.ventage.valuo_monitoring_service.domain.model.valueobjects.OperationType;
import pe.ventage.valuo_monitoring_service.interfaces.rest.resources.TriggerTrainingResource;

public final class TriggerTrainingCommandFromResourceAssembler {

    private TriggerTrainingCommandFromResourceAssembler() {}

    public static TriggerTrainingCommand toCommandFromResource(TriggerTrainingResource resource, String userEmail) {
        return new TriggerTrainingCommand(
                OperationType.fromString(resource.operationType()),
                resource.datasetVersion() != null ? resource.datasetVersion() : "v1.0.0",
                resource.targetMetric() != null ? resource.targetMetric() : "r2",
                userEmail != null ? userEmail : "mock-admin@valuo.pe",
                resource.maxDepth(),
                resource.learningRate()
        );
    }
}
