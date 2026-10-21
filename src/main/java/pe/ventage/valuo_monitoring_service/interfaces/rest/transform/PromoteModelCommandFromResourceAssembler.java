package pe.ventage.valuo_monitoring_service.interfaces.rest.transform;

import pe.ventage.valuo_monitoring_service.domain.model.commands.PromoteModelCommand;
import pe.ventage.valuo_monitoring_service.domain.model.valueobjects.OperationType;
import pe.ventage.valuo_monitoring_service.interfaces.rest.resources.PromoteModelResource;

public final class PromoteModelCommandFromResourceAssembler {

    private PromoteModelCommandFromResourceAssembler() {}

    public static PromoteModelCommand toCommandFromResource(PromoteModelResource resource, String userEmail) {
        String effectiveOp = resource != null && resource.operationType() != null ? resource.operationType() : "VENTA";
        String effectiveReason = resource != null ? resource.reason() : null;
        return new PromoteModelCommand(
                OperationType.fromString(effectiveOp),
                effectiveReason,
                userEmail != null ? userEmail : "admin@valuo.pe"
        );
    }
}
