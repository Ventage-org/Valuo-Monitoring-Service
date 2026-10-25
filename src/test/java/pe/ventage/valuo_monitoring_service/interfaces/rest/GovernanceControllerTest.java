package pe.ventage.valuo_monitoring_service.interfaces.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pe.ventage.valuo_monitoring_service.domain.model.aggregates.ModelTrainingJob;
import pe.ventage.valuo_monitoring_service.domain.model.aggregates.ModelTransitionLog;
import pe.ventage.valuo_monitoring_service.domain.model.commands.PromoteModelCommand;
import pe.ventage.valuo_monitoring_service.domain.model.commands.RollbackModelCommand;
import pe.ventage.valuo_monitoring_service.domain.model.commands.TriggerTrainingCommand;
import pe.ventage.valuo_monitoring_service.domain.model.exceptions.TrainingConflictException;
import pe.ventage.valuo_monitoring_service.domain.model.queries.*;
import pe.ventage.valuo_monitoring_service.domain.model.valueobjects.ModelStage;
import pe.ventage.valuo_monitoring_service.domain.model.valueobjects.OperationType;
import pe.ventage.valuo_monitoring_service.domain.services.GovernanceCommandService;
import pe.ventage.valuo_monitoring_service.domain.services.GovernanceQueryService;
import pe.ventage.valuo_monitoring_service.interfaces.rest.errors.GlobalExceptionHandler;
import pe.ventage.valuo_monitoring_service.interfaces.rest.resources.PromoteModelResource;
import pe.ventage.valuo_monitoring_service.interfaces.rest.resources.RollbackModelResource;
import pe.ventage.valuo_monitoring_service.interfaces.rest.resources.TriggerTrainingResource;
import pe.ventage.valuo_monitoring_service.interfaces.rest.resources.UpdateIpcResource;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class GovernanceControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private GovernanceCommandService commandService;

    @Mock
    private GovernanceQueryService queryService;

    @InjectMocks
    private GovernanceController governanceController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(governanceController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ==========================================
    // 1. REENTRENAMIENTO (VENTA Y ALQUILER)
    // ==========================================

    @Test
    @DisplayName("POST /api/v1/governance/training - Venta exitoso retorna 201 Created")
    void triggerTraining_venta_success() throws Exception {
        TriggerTrainingResource request = new TriggerTrainingResource("VENTA", "v1.0.0", "r2", 6, 0.04);
        ModelTrainingJob job = new ModelTrainingJob(
                new TriggerTrainingCommand(OperationType.VENTA, "v1.0.0", "r2", "admin@valuo.pe", 6, 0.04),
                "job-venta-123"
        );

        when(commandService.handle(any(TriggerTrainingCommand.class))).thenReturn(job);

        mockMvc.perform(post("/api/v1/governance/training")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.job_id").value("job-venta-123"))
                .andExpect(jsonPath("$.operation_type").value("VENTA"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("POST /api/v1/governance/training - Alquiler exitoso retorna 201 Created")
    void triggerTraining_alquiler_success() throws Exception {
        TriggerTrainingResource request = new TriggerTrainingResource("ALQUILER", "v1.0.0", "r2", 6, 0.035);
        ModelTrainingJob job = new ModelTrainingJob(
                new TriggerTrainingCommand(OperationType.ALQUILER, "v1.0.0", "r2", "admin@valuo.pe", 6, 0.035),
                "job-alquiler-456"
        );

        when(commandService.handle(any(TriggerTrainingCommand.class))).thenReturn(job);

        mockMvc.perform(post("/api/v1/governance/training")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.job_id").value("job-alquiler-456"))
                .andExpect(jsonPath("$.operation_type").value("ALQUILER"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("POST /api/v1/governance/training - Bloqueo por regla de concurrencia retorna 409 Conflict")
    void triggerTraining_concurrencyConflict_returns409() throws Exception {
        TriggerTrainingResource request = new TriggerTrainingResource("ALQUILER", "v1.0.0", "r2", 6, 0.035);

        when(commandService.handle(any(TriggerTrainingCommand.class)))
                .thenThrow(new TrainingConflictException("Ya existe un entrenamiento en curso (Job ID: job-alquiler-456). Solo se permite un job simultáneo."));

        mockMvc.perform(post("/api/v1/governance/training")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Training Conflict"))
                .andExpect(jsonPath("$.detail").value("Ya existe un entrenamiento en curso (Job ID: job-alquiler-456). Solo se permite un job simultáneo."));
    }

    @Test
    @DisplayName("POST /api/v1/governance/training - Validación falla si operation_type es inválido (400 Bad Request)")
    void triggerTraining_invalidOperationType_returns400() throws Exception {
        String invalidJson = """
                {
                  "operation_type": "HIPOTECARIO",
                  "max_depth": 6
                }
                """;

        mockMvc.perform(post("/api/v1/governance/training")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"));
    }

    // ==========================================
    // 2. CONSULTA Y LISTADO DE JOBS
    // ==========================================

    @Test
    @DisplayName("GET /api/v1/governance/jobs/{jobId} - Retorna 200 con estado del job de Alquiler")
    void getJobStatus_alquiler_success() throws Exception {
        ModelTrainingJob job = new ModelTrainingJob(
                new TriggerTrainingCommand(OperationType.ALQUILER, "v1.0.0", "r2", "admin@valuo.pe", 6, 0.035),
                "job-alquiler-456"
        );
        job.complete("xgboost_alquiler_v3", 0.76, 450.0, 11.8);

        when(queryService.handle(any(GetJobStatusQuery.class))).thenReturn(Optional.of(job));

        mockMvc.perform(get("/api/v1/governance/jobs/job-alquiler-456"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.job_id").value("job-alquiler-456"))
                .andExpect(jsonPath("$.operation_type").value("ALQUILER"))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.model_version").value("xgboost_alquiler_v3"))
                .andExpect(jsonPath("$.metrics_r2").value(0.76))
                .andExpect(jsonPath("$.metrics_mape").value(11.8));
    }

    @Test
    @DisplayName("GET /api/v1/governance/jobs - Historial paginado de entrenamientos para Alquiler")
    void getTrainingJobs_alquiler_success() throws Exception {
        ModelTrainingJob job = new ModelTrainingJob(
                new TriggerTrainingCommand(OperationType.ALQUILER, "v1.0.0", "r2", "admin@valuo.pe", 6, 0.035),
                "job-alquiler-456"
        );
        Page<ModelTrainingJob> page = new PageImpl<>(List.of(job));

        when(queryService.handle(any(GetTrainingHistoryQuery.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/governance/jobs?operationType=ALQUILER&page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].job_id").value("job-alquiler-456"))
                .andExpect(jsonPath("$.content[0].operation_type").value("ALQUILER"))
                .andExpect(jsonPath("$.total_elements").value(1));
    }

    // ==========================================
    // 3. PROMOCIÓN Y ROLLBACK (VENTA Y ALQUILER)
    // ==========================================

    @Test
    @DisplayName("POST /api/v1/governance/promote - Promoción exitosa para Alquiler con deltas")
    void promoteModel_alquiler_success() throws Exception {
        PromoteModelResource resource = new PromoteModelResource("ALQUILER", "Validación exitosa modelo alquiler v3");
        ModelTransitionLog transitionLog = ModelTransitionLog.forPromotion(
                OperationType.ALQUILER,
                "3",
                "2",
                ModelStage.PRODUCTION,
                "admin@valuo.pe",
                "Validación exitosa modelo alquiler v3",
                -0.95,
                0.0015,
                -35.0,
                5.2,
                true
        );

        when(commandService.handle(any(PromoteModelCommand.class))).thenReturn(transitionLog);

        mockMvc.perform(post("/api/v1/governance/promote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resource)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("PROMOTION"))
                .andExpect(jsonPath("$.operation_type").value("ALQUILER"))
                .andExpect(jsonPath("$.new_version").value("3"))
                .andExpect(jsonPath("$.previous_version").value("2"))
                .andExpect(jsonPath("$.deltas.delta_mape_pct").value(-0.95))
                .andExpect(jsonPath("$.deltas.mejora_mape").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/governance/rollback - Rollback exitoso para Alquiler")
    void rollbackModel_alquiler_success() throws Exception {
        RollbackModelResource resource = new RollbackModelResource("ALQUILER", "2", "Rollback preventivo en alquiler");
        ModelTransitionLog transitionLog = ModelTransitionLog.forRollback(
                OperationType.ALQUILER,
                "2",
                "3",
                "admin@valuo.pe",
                "Rollback preventivo en alquiler",
                0.95,
                -0.0015,
                35.0,
                -5.2,
                false
        );

        when(commandService.handle(any(RollbackModelCommand.class))).thenReturn(transitionLog);

        mockMvc.perform(post("/api/v1/governance/rollback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resource)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("ROLLBACK"))
                .andExpect(jsonPath("$.operation_type").value("ALQUILER"))
                .andExpect(jsonPath("$.new_version").value("2"))
                .andExpect(jsonPath("$.previous_version").value("3"))
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    // ==========================================
    // 4. BENCHMARK E HISTORIAL MLFLOW (ALQUILER)
    // ==========================================

    @Test
    @DisplayName("GET /api/v1/governance/benchmark - Benchmark para Alquiler con solo_ultimo=true")
    void getBenchmark_alquiler_success() throws Exception {
        ModelBenchmarkResult result = new ModelBenchmarkResult(
                "alquiler",
                12.0,
                new ProductionModelSummary("xgboost_alquiler", "2", "Production", "run-alq-2", null, "2026-10-10", "models:/xgboost_alquiler/2"),
                List.of(),
                1,
                "2026-10-24T20:00:00Z"
        );

        when(queryService.handle(any(GetModelBenchmarkQuery.class))).thenReturn(result);

        mockMvc.perform(get("/api/v1/governance/benchmark?operationType=ALQUILER&solo_ultimo=true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo_operacion").value("alquiler"))
                .andExpect(jsonPath("$.benchmark_referencial_mape").value(12.0))
                .andExpect(jsonPath("$.modelo_produccion.nombre_modelo").value("xgboost_alquiler"));
    }

    @Test
    @DisplayName("GET /api/v1/governance/models/history - Historial de versiones MLflow para Alquiler")
    void getModelHistory_alquiler_success() throws Exception {
        when(queryService.getModelHistory(OperationType.ALQUILER)).thenReturn(Map.of(
                "tipo_operacion", "alquiler",
                "nombre_modelo", "xgboost_alquiler",
                "total_versiones", 2,
                "versiones", List.of(
                        Map.of("version", "2", "stage", "Production"),
                        Map.of("version", "1", "stage", "Archived")
                )
        ));

        mockMvc.perform(get("/api/v1/governance/models/history?operationType=ALQUILER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo_operacion").value("alquiler"))
                .andExpect(jsonPath("$.total_versiones").value(2))
                .andExpect(jsonPath("$.versiones[0].version").value("2"));
    }

    // ==========================================
    // 5. IPC Y DISPONIBILIDAD
    // ==========================================

    @Test
    @DisplayName("GET /api/v1/governance/ipc - Lectura de factor IPC vigente")
    void getIpcConfig_success() throws Exception {
        when(queryService.getIpcConfig()).thenReturn(Map.of(
                "ipc_vigente", Map.of("valor", 169.5, "factor", 1.695, "periodo", "2026-Q2")
        ));

        mockMvc.perform(get("/api/v1/governance/ipc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ipc_vigente.valor").value(169.5))
                .andExpect(jsonPath("$.ipc_vigente.periodo").value("2026-Q2"));
    }

    @Test
    @DisplayName("PUT /api/v1/governance/ipc - Actualización en caliente de factor IPC")
    void updateIpcConfig_success() throws Exception {
        UpdateIpcResource resource = new UpdateIpcResource(169.5, "2026-Q2", "INEI", "Joaquin Cortez", "Ajuste", "ambos");

        when(commandService.updateIpcConfig(eq(169.5), eq("2026-Q2"), eq("INEI"), eq("Joaquin Cortez"), eq("Ajuste"), eq("ambos")))
                .thenReturn(Map.of("exito", true, "mensaje", "IPC actualizado"));

        mockMvc.perform(put("/api/v1/governance/ipc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resource)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exito").value(true));
    }

    @Test
    @DisplayName("GET /api/v1/governance/training/availability - Consulta de disponibilidad de entrenamiento")
    void getTrainingAvailability_success() throws Exception {
        when(queryService.isTrainingAvailable()).thenReturn(true);

        mockMvc.perform(get("/api/v1/governance/training/availability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disponible_para_entrenar").value(true));
    }
}
