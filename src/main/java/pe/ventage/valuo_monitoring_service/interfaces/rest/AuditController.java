package pe.ventage.valuo_monitoring_service.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.ventage.valuo_monitoring_service.domain.model.aggregates.ModelTransitionLog;
import pe.ventage.valuo_monitoring_service.domain.model.queries.GetAuditLogsQuery;
import pe.ventage.valuo_monitoring_service.domain.model.valueobjects.OperationType;
import pe.ventage.valuo_monitoring_service.domain.services.AuditQueryService;
import pe.ventage.valuo_monitoring_service.interfaces.rest.resources.ModelTransitionLogResponseResource;
import pe.ventage.valuo_monitoring_service.interfaces.rest.resources.PageResponseResource;
import pe.ventage.valuo_monitoring_service.interfaces.rest.transform.ModelTransitionLogResourceFromEntityAssembler;

import java.util.List;

@RestController
@RequestMapping("/api/v1/audit")
@Tag(name = "Auditoría", description = "Trazabilidad inmutable de transiciones y cambios de modelos ML")
public class AuditController {

    private final AuditQueryService auditQueryService;

    public AuditController(AuditQueryService auditQueryService) {
        this.auditQueryService = auditQueryService;
    }

    @GetMapping("/logs")
    @Operation(summary = "Consultar logs de auditoría", description = "Listado paginado de acciones de modelos (Reentrenamiento, Promoción, Rollback) con deltas métricos")
    public ResponseEntity<PageResponseResource<ModelTransitionLogResponseResource>> getAuditLogs(
            @RequestParam(required = false) String operationType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        OperationType op = (operationType != null && !operationType.isBlank() && !"ALL".equalsIgnoreCase(operationType))
                ? OperationType.fromString(operationType)
                : null;
        GetAuditLogsQuery query = new GetAuditLogsQuery(
                op,
                PageRequest.of(page, Math.min(size, 100))
        );
        Page<ModelTransitionLog> pageResult = auditQueryService.handle(query);

        List<ModelTransitionLogResponseResource> items = pageResult.getContent().stream()
                .map(ModelTransitionLogResourceFromEntityAssembler::toResourceFromEntity)
                .toList();

        PageResponseResource<ModelTransitionLogResponseResource> response = new PageResponseResource<>(
                items,
                pageResult.getNumber(),
                pageResult.getSize(),
                pageResult.getTotalElements(),
                pageResult.getTotalPages(),
                pageResult.isLast()
        );
        return ResponseEntity.ok(response);
    }
}
