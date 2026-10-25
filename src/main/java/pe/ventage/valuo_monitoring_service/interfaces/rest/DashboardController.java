package pe.ventage.valuo_monitoring_service.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.ventage.valuo_monitoring_service.domain.model.queries.*;
import pe.ventage.valuo_monitoring_service.domain.model.valueobjects.OperationType;
import pe.ventage.valuo_monitoring_service.domain.services.DashboardQueryService;
import pe.ventage.valuo_monitoring_service.interfaces.rest.resources.*;
import pe.ventage.valuo_monitoring_service.interfaces.rest.transform.DashboardResourceAssembler;

@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard", description = "Vista principal de observabilidad y métricas de modelos")
public class DashboardController {

    private final DashboardQueryService dashboardQueryService;

    public DashboardController(DashboardQueryService dashboardQueryService) {
        this.dashboardQueryService = dashboardQueryService;
    }

    @GetMapping("/overview")
    @Operation(summary = "Resumen ejecutivo del dashboard", description = "Combina salud del modelo, data drift y concept drift en una sola respuesta")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resumen obtenido exitosamente"),
            @ApiResponse(responseCode = "400", description = "Tipo de operación inválido")
    })
    public ResponseEntity<DashboardOverviewResponseResource> getOverview(
            @Parameter(description = "Tipo de operación: VENTA o ALQUILER")
            @RequestParam(defaultValue = "VENTA") String operationType
    ) {
        GetDashboardOverviewQuery query = new GetDashboardOverviewQuery(OperationType.fromString(operationType));
        DashboardOverviewResult result = dashboardQueryService.handle(query);
        return ResponseEntity.ok(DashboardResourceAssembler.toResource(result));
    }

    @GetMapping("/health")
    @Operation(summary = "Salud del modelo activo", description = "Retorna métricas de rendimiento (R2, MAE, MAPE) del modelo en producción")
    public ResponseEntity<ModelHealthResponseResource> getHealth(
            @RequestParam(defaultValue = "VENTA") String operationType
    ) {
        GetModelHealthQuery query = new GetModelHealthQuery(OperationType.fromString(operationType));
        ModelHealthResult result = dashboardQueryService.handle(query);
        return ResponseEntity.ok(DashboardResourceAssembler.toResource(result));
    }

    @GetMapping("/data-drift")
    @Operation(summary = "Métricas de Data Drift", description = "Retorna deriva de covariables por feature para la ventana temporal indicada")
    public ResponseEntity<DataDriftResponseResource> getDataDrift(
            @RequestParam(defaultValue = "VENTA") String operationType,
            @RequestParam(defaultValue = "30d") String range
    ) {
        GetDataDriftQuery query = new GetDataDriftQuery(OperationType.fromString(operationType), range);
        DataDriftResult result = dashboardQueryService.handle(query);
        return ResponseEntity.ok(DashboardResourceAssembler.toResource(result));
    }

    @GetMapping("/concept-drift")
    @Operation(summary = "Métricas de Concept Drift", description = "Retorna métricas Wasserstein, PSI y KS sobre la variable target")
    public ResponseEntity<ConceptDriftResponseResource> getConceptDrift(
            @RequestParam(defaultValue = "VENTA") String operationType,
            @RequestParam(defaultValue = "30d") String range
    ) {
        GetConceptDriftQuery query = new GetConceptDriftQuery(OperationType.fromString(operationType), range);
        ConceptDriftResult result = dashboardQueryService.handle(query);
        return ResponseEntity.ok(DashboardResourceAssembler.toResource(result));
    }

    @GetMapping("/resources")
    @Operation(summary = "Telemetría de recursos del contenedor ML", description = "Retorna telemetría en tiempo real: CPU %, memoria RAM usada y total (MB), disco y tiempo de actividad (uptime).")
    public ResponseEntity<java.util.Map<String, Object>> getResources() {
        return ResponseEntity.ok(dashboardQueryService.getSystemResources());
    }

    @GetMapping("/latency")
    @Operation(summary = "Latencia P95 y Hash de Artefactos", description = "Retorna latencia P50, P95, P99 en ms, umbral SLA (100ms) y verificación de integridad sha256 del modelo en producción.")
    public ResponseEntity<java.util.Map<String, Object>> getLatency(
            @RequestParam(defaultValue = "VENTA") String operationType
    ) {
        return ResponseEntity.ok(dashboardQueryService.getLatencyMetrics(OperationType.fromString(operationType)));
    }
}
