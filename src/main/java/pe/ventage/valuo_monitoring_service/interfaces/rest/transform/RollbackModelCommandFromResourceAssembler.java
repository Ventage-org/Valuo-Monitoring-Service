package pe.ventage.valuo_monitoring_service.interfaces.rest.transform;

import pe.ventage.valuo_monitoring_service.domain.model.commands.RollbackModelCommand;
import pe.ventage.valuo_monitoring_service.domain.model.valueobjects.OperationType;
import pe.ventage.valuo_monitoring_service.interfaces.rest.resources.RollbackModelResource;

public final class RollbackModelCommandFromResourceAssembler {

    private RollbackModelCommandFromResourceAssembler() {}

    public static RollbackModelCommand toCommandFromResource(RollbackModelResource resource, String userEmail) {
        return new RollbackModelCommand(
                OperationType.fromString(resource.operationType()),
                resource.targetVersion(),
                resource.reason(),
                userEmail != null ? userEmail : "mock-admin@valuo.pe"
        );
    }
}
