package pe.ventage.valuo_monitoring_service.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.ventage.valuo_monitoring_service.domain.model.aggregates.ModelTrainingJob;
import pe.ventage.valuo_monitoring_service.domain.model.aggregates.ModelTransitionLog;
import pe.ventage.valuo_monitoring_service.domain.model.commands.PromoteModelCommand;
import pe.ventage.valuo_monitoring_service.domain.model.commands.RollbackModelCommand;
import pe.ventage.valuo_monitoring_service.domain.model.commands.TriggerTrainingCommand;
import pe.ventage.valuo_monitoring_service.domain.model.exceptions.ModelTrainingJobNotFoundException;
import pe.ventage.valuo_monitoring_service.domain.model.queries.*;
import pe.ventage.valuo_monitoring_service.domain.model.valueobjects.OperationType;
import pe.ventage.valuo_monitoring_service.domain.services.GovernanceCommandService;
import pe.ventage.valuo_monitoring_service.domain.services.GovernanceQueryService;
import pe.ventage.valuo_monitoring_service.interfaces.rest.resources.*;
import pe.ventage.valuo_monitoring_service.interfaces.rest.transform.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/governance")
@Tag(name = "Gobernanza", description = "Ciclo de vida de modelos ML: reentrenamiento, promoción y rollback")
public class GovernanceController {

    private final GovernanceCommandService commandService;
    private final GovernanceQueryService queryService;

    // Mock until centralized JWT authentication microservice is integrated
    private static final String MOCK_ADMIN_EMAIL = "admin@valuo.pe";

    public GovernanceController(
            GovernanceCommandService commandService,
            GovernanceQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    @PostMapping("/training")
    @Operation(summary = "Disparar reentrenamiento manual", description = "Inicia un job asíncrono en FastAPI. No promueve automáticamente a producción.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Entrenamiento iniciado"),
            @ApiResponse(responseCode = "400", description = "Parámetros inválidos"),
            @ApiResponse(responseCode = "503", description = "Servicio ML no disponible")
    })
    public ResponseEntity<TrainingJobResponseResource> triggerTraining(
            @Valid @RequestBody TriggerTrainingResource resource
    ) {
        TriggerTrainingCommand command = TriggerTrainingCommandFromResourceAssembler.toCommandFromResource(resource, MOCK_ADMIN_EMAIL);
        ModelTrainingJob job = commandService.handle(command);
        TrainingJobResponseResource body = TrainingJobResourceFromEntityAssembler.toResourceFromEntity(job);
        return ResponseEntity.created(URI.create("/api/v1/governance/jobs/" + job.getJobId())).body(body);
    }

    @GetMapping("/jobs/{jobId}")
    @Operation(summary = "Consultar estado de un job de entrenamiento", description = "Sincroniza síncronamente el estado con el servicio ML dentro del request.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Job encontrado"),
            @ApiResponse(responseCode = "404", description = "Job no existe")
    })
    public ResponseEntity<TrainingJobResponseResource> getJobStatus(@PathVariable String jobId) {
        GetJobStatusQuery query = new GetJobStatusQuery(jobId);
        ModelTrainingJob job = queryService.handle(query)
                .orElseThrow(() -> new ModelTrainingJobNotFoundException(jobId));
        return ResponseEntity.ok(TrainingJobResourceFromEntityAssembler.toResourceFromEntity(job));
    }

    @GetMapping("/jobs")
    @Operation(summary = "Historial de entrenamientos", description = "Listado paginado de jobs ejecutados")
    public ResponseEntity<PageResponseResource<TrainingJobResponseResource>> getTrainingJobs(
            @RequestParam(defaultValue = "VENTA") String operationType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        GetTrainingHistoryQuery query = new GetTrainingHistoryQuery(
                OperationType.fromString(operationType),
                PageRequest.of(page, Math.min(size, 50))
        );
        Page<ModelTrainingJob> pageResult = queryService.handle(query);
        List<TrainingJobResponseResource> items = pageResult.getContent().stream()
                .map(TrainingJobResourceFromEntityAssembler::toResourceFromEntity)
                .toList();

        PageResponseResource<TrainingJobResponseResource> response = new PageResponseResource<>(
                items,
                pageResult.getNumber(),
                pageResult.getSize(),
                pageResult.getTotalElements(),
                pageResult.getTotalPages(),
                pageResult.isLast()
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/promote")
    @Operation(summary = "Promover modelo candidato a producción", description = "Promueve automáticamente el último candidato a producción. No requiere enviar versión ni target_stage.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Modelo promovido exitosamente"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos o modelo no apto"),
            @ApiResponse(responseCode = "503", description = "Servicio ML no disponible")
    })
    public ResponseEntity<ModelTransitionLogResponseResource> promoteModel(
            @Valid @RequestBody PromoteModelResource resource
    ) {
        PromoteModelCommand command = PromoteModelCommandFromResourceAssembler.toCommandFromResource(resource, MOCK_ADMIN_EMAIL);
        ModelTransitionLog transitionLog = commandService.handle(command);
        return ResponseEntity.ok(ModelTransitionLogResourceFromEntityAssembler.toResourceFromEntity(transitionLog));
    }

    @PostMapping("/rollback")
    @Operation(summary = "Rollback de modelo en producción", description = "Restaura una versión previa y registra el evento en auditoría")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rollback completado"),
            @ApiResponse(responseCode = "400", description = "Versión objetivo inválida"),
            @ApiResponse(responseCode = "503", description = "Servicio ML no disponible")
    })
    public ResponseEntity<ModelTransitionLogResponseResource> rollbackModel(
            @Valid @RequestBody RollbackModelResource resource
    ) {
        RollbackModelCommand command = RollbackModelCommandFromResourceAssembler.toCommandFromResource(resource, MOCK_ADMIN_EMAIL);
        ModelTransitionLog transitionLog = commandService.handle(command);
        return ResponseEntity.ok(ModelTransitionLogResourceFromEntityAssembler.toResourceFromEntity(transitionLog));
    }

    @GetMapping("/benchmark")
    @Operation(summary = "Comparativa Benchmark: Producción vs Candidatos", description = "Compara el modelo actualmente en producción contra los modelos candidatos reentrenados para decidir promociones.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Benchmark obtenido exitosamente"),
            @ApiResponse(responseCode = "400", description = "Tipo de operación inválido"),
            @ApiResponse(responseCode = "503", description = "Servicio ML no disponible")
    })
    public ResponseEntity<ModelBenchmarkResponseResource> getBenchmark(
            @RequestParam(defaultValue = "VENTA") String operationType,
            @RequestParam(name = "solo_ultimo", defaultValue = "true") boolean soloUltimoSnake,
            @RequestParam(name = "soloUltimo", required = false) Boolean soloUltimoCamel
    ) {
        boolean effectiveSoloUltimo = soloUltimoCamel != null ? soloUltimoCamel : soloUltimoSnake;
        GetModelBenchmarkQuery query = new GetModelBenchmarkQuery(OperationType.fromString(operationType), effectiveSoloUltimo);
        ModelBenchmarkResult result = queryService.handle(query);
        return ResponseEntity.ok(ModelBenchmarkResourceAssembler.toResource(result));
    }

    @GetMapping("/ipc")
    @Operation(summary = "Consultar factor IPC vigente", description = "Retorna el factor IPC macroeconómico activo en memoria RAM para venta y alquiler.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Configuración IPC obtenida"),
            @ApiResponse(responseCode = "503", description = "Servicio ML no disponible")
    })
    public ResponseEntity<java.util.Map<String, Object>> getIpcConfig() {
        return ResponseEntity.ok(queryService.getIpcConfig());
    }

    @PutMapping("/ipc")
    @Operation(summary = "Actualizar factor IPC en caliente", description = "Actualiza el factor macroeconómico en memoria RAM sin downtime y persiste en bitácora.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Factor IPC actualizado exitosamente"),
            @ApiResponse(responseCode = "400", description = "Parámetros inválidos"),
            @ApiResponse(responseCode = "500", description = "Error al actualizar en el servicio ML")
    })
    public ResponseEntity<java.util.Map<String, Object>> updateIpcConfig(
            @Valid @RequestBody pe.ventage.valuo_monitoring_service.interfaces.rest.resources.UpdateIpcResource resource
    ) {
        String actor = resource.actualizadoPor() != null && !resource.actualizadoPor().isBlank()
                ? resource.actualizadoPor()
                : MOCK_ADMIN_EMAIL;
        java.util.Map<String, Object> response = commandService.updateIpcConfig(
                resource.valor(),
                resource.periodo(),
                resource.fuente(),
                actor,
                resource.nota(),
                resource.aplicarA()
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/models/history")
    @Operation(summary = "Historial de versiones en MLflow", description = "Lista todas las versiones de modelos registradas en MLflow Model Registry para venta o alquiler.")
    public ResponseEntity<java.util.Map<String, Object>> getModelHistory(
            @RequestParam(defaultValue = "VENTA") String operationType
    ) {
        return ResponseEntity.ok(queryService.getModelHistory(OperationType.fromString(operationType)));
    }

    @GetMapping("/training/availability")
    @Operation(summary = "Consultar disponibilidad para nuevo entrenamiento", description = "Verifica si el servicio ML y la base de datos se encuentran libres o si hay un job en curso.")
    public ResponseEntity<java.util.Map<String, Object>> getTrainingAvailability() {
        boolean available = queryService.isTrainingAvailable();
        return ResponseEntity.ok(java.util.Map.of(
                "disponible_para_entrenar", available,
                "mensaje", available ? "Servicio disponible para iniciar un nuevo entrenamiento" : "Servicio ocupado ejecutando otro entrenamiento"
        ));
    }
}
