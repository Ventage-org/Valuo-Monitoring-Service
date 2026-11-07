package pe.ventage.valuo_monitoring_service.interfaces.rest.transform;

import pe.ventage.valuo_monitoring_service.domain.model.commands.DiscardModelCommand;
import pe.ventage.valuo_monitoring_service.domain.model.valueobjects.OperationType;
import pe.ventage.valuo_monitoring_service.interfaces.rest.resources.DiscardModelResource;

public class DiscardModelCommandFromResourceAssembler {

    public static DiscardModelCommand toCommandFromResource(DiscardModelResource resource, String userEmail) {
        return new DiscardModelCommand(
                OperationType.fromString(resource.operationType()),
                resource.version(),
                resource.reason(),
                userEmail
        );
    }
}

