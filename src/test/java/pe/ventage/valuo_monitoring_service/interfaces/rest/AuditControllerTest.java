package pe.ventage.valuo_monitoring_service.interfaces.rest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pe.ventage.valuo_monitoring_service.domain.model.aggregates.ModelTransitionLog;
import pe.ventage.valuo_monitoring_service.domain.model.queries.GetAuditLogsQuery;
import pe.ventage.valuo_monitoring_service.domain.model.valueobjects.ModelStage;
import pe.ventage.valuo_monitoring_service.domain.model.valueobjects.OperationType;
import pe.ventage.valuo_monitoring_service.domain.services.AuditQueryService;
import pe.ventage.valuo_monitoring_service.interfaces.rest.errors.GlobalExceptionHandler;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuditControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AuditQueryService auditQueryService;

    @InjectMocks
    private AuditController auditController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(auditController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/audit/logs - Retorna listado de logs paginado con deltas y versiones (Promoción)")
    void getAuditLogs_promotion_success() throws Exception {
        ModelTransitionLog logEntry = ModelTransitionLog.forPromotion(
                OperationType.VENTA,
                "10",
                "9",
                ModelStage.PRODUCTION,
                "admin@valuo.pe",
                "Aprobado por comité",
                -0.82,
                0.0001,
                -1200.0,
                10.0,
                true
        );

        Page<ModelTransitionLog> page = new PageImpl<>(List.of(logEntry));
        when(auditQueryService.handle(any(GetAuditLogsQuery.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/audit/logs?page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].action").value("PROMOTION"))
                .andExpect(jsonPath("$.content[0].operation_type").value("VENTA"))
                .andExpect(jsonPath("$.content[0].new_version").value("10"))
                .andExpect(jsonPath("$.content[0].previous_version").value("9"))
                .andExpect(jsonPath("$.content[0].performed_by").value("admin@valuo.pe"))
                .andExpect(jsonPath("$.content[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.content[0].deltas.delta_mape_pct").value(-0.82))
                .andExpect(jsonPath("$.content[0].deltas.mejora_mape").value(true));
    }

    @Test
    @DisplayName("GET /api/v1/audit/logs (Alquiler) - Filtro por tipo de operación Alquiler con eventos de Reentrenamiento")
    void getAuditLogs_alquiler_training_success() throws Exception {
        ModelTransitionLog logStart = ModelTransitionLog.forTraining(
                OperationType.ALQUILER,
                "xgboost_alquiler_v1",
                "job-alq-789",
                "admin@valuo.pe",
                "Reentrenamiento programado"
        );
        ModelTransitionLog logCompleted = ModelTransitionLog.forTrainingCompleted(
                OperationType.ALQUILER,
                "xgboost_alquiler_v2",
                "xgboost_alquiler_v1",
                "admin@valuo.pe",
                0.765,
                420.0,
                11.20
        );

        Page<ModelTransitionLog> page = new PageImpl<>(List.of(logCompleted, logStart));
        when(auditQueryService.handle(any(GetAuditLogsQuery.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/audit/logs?operationType=ALQUILER&page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].action").value("TRAINING"))
                .andExpect(jsonPath("$.content[0].operation_type").value("ALQUILER"))
                .andExpect(jsonPath("$.content[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.content[0].new_version").value("xgboost_alquiler_v2"))
                .andExpect(jsonPath("$.content[1].action").value("TRAINING"))
                .andExpect(jsonPath("$.content[1].status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.total_elements").value(2));
    }

    @Test
    @DisplayName("GET /api/v1/audit/logs (Alquiler) - Auditoría de Rollback en Alquiler")
    void getAuditLogs_alquiler_rollback_success() throws Exception {
        ModelTransitionLog logRollback = ModelTransitionLog.forRollback(
                OperationType.ALQUILER,
                "1",
                "2",
                "admin@valuo.pe",
                "Degradación detectada",
                0.90,
                -0.0010,
                30.0,
                -4.0,
                false
        );

        Page<ModelTransitionLog> page = new PageImpl<>(List.of(logRollback));
        when(auditQueryService.handle(any(GetAuditLogsQuery.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/audit/logs?operationType=ALQUILER&page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].action").value("ROLLBACK"))
                .andExpect(jsonPath("$.content[0].operation_type").value("ALQUILER"))
                .andExpect(jsonPath("$.content[0].new_version").value("1"))
                .andExpect(jsonPath("$.content[0].previous_version").value("2"))
                .andExpect(jsonPath("$.content[0].status").value("SUCCESS"));
    }
}
