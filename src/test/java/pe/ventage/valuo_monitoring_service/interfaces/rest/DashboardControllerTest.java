package pe.ventage.valuo_monitoring_service.interfaces.rest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pe.ventage.valuo_monitoring_service.domain.model.queries.*;
import pe.ventage.valuo_monitoring_service.domain.model.valueobjects.OperationType;
import pe.ventage.valuo_monitoring_service.domain.services.DashboardQueryService;
import pe.ventage.valuo_monitoring_service.interfaces.rest.errors.GlobalExceptionHandler;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

    private MockMvc mockMvc;

    @Mock
    private DashboardQueryService dashboardQueryService;

    @InjectMocks
    private DashboardController dashboardController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(dashboardController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ==========================================
    // VENTA TESTS
    // ==========================================

    @Test
    @DisplayName("GET /api/v1/dashboard/health (Venta) - Retorna métricas de salud del modelo")
    void getHealth_venta_success() throws Exception {
        ModelHealthResult result = new ModelHealthResult("VENTA", "v8", 0.747, 50897.0, 14.15, "HEALTHY", LocalDateTime.now());
        when(dashboardQueryService.handle(any(GetModelHealthQuery.class))).thenReturn(result);

        mockMvc.perform(get("/api/v1/dashboard/health?operationType=VENTA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operation_type").value("VENTA"))
                .andExpect(jsonPath("$.active_model_version").value("v8"))
                .andExpect(jsonPath("$.r2").value(0.747))
                .andExpect(jsonPath("$.status").value("HEALTHY"));
    }

    @Test
    @DisplayName("GET /api/v1/dashboard/overview (Venta) - Resumen ejecutivo de observabilidad")
    void getOverview_venta_success() throws Exception {
        ModelHealthResult health = new ModelHealthResult("VENTA", "v8", 0.747, 50897.0, 14.15, "HEALTHY", LocalDateTime.now());
        DataDriftResult dataDrift = new DataDriftResult("VENTA", "30d", false, 0.0, 0, 33, List.of());
        ConceptDriftResult conceptDrift = new ConceptDriftResult("VENTA", "30d", false, 0.01, 0.02, 0.01, "Estable");

        DashboardOverviewResult result = new DashboardOverviewResult(
                health,
                dataDrift,
                conceptDrift,
                "HEALTHY",
                List.of()
        );
        when(dashboardQueryService.handle(any(GetDashboardOverviewQuery.class))).thenReturn(result);

        mockMvc.perform(get("/api/v1/dashboard/overview?operationType=VENTA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.global_status").value("HEALTHY"))
                .andExpect(jsonPath("$.health.active_model_version").value("v8"));
    }

    // ==========================================
    // ALQUILER TESTS
    // ==========================================

    @Test
    @DisplayName("GET /api/v1/dashboard/health (Alquiler) - Retorna métricas de salud del modelo de alquiler")
    void getHealth_alquiler_success() throws Exception {
        ModelHealthResult result = new ModelHealthResult("ALQUILER", "xgboost_alquiler_v2", 0.765, 420.50, 11.20, "HEALTHY", LocalDateTime.now());
        when(dashboardQueryService.handle(any(GetModelHealthQuery.class))).thenReturn(result);

        mockMvc.perform(get("/api/v1/dashboard/health?operationType=ALQUILER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operation_type").value("ALQUILER"))
                .andExpect(jsonPath("$.active_model_version").value("xgboost_alquiler_v2"))
                .andExpect(jsonPath("$.r2").value(0.765))
                .andExpect(jsonPath("$.mae").value(420.50))
                .andExpect(jsonPath("$.mape").value(11.20))
                .andExpect(jsonPath("$.status").value("HEALTHY"));
    }

    @Test
    @DisplayName("GET /api/v1/dashboard/data-drift (Alquiler) - Deriva de covariables en alquiler")
    void getDataDrift_alquiler_success() throws Exception {
        DataDriftResult dataDrift = new DataDriftResult(
                "ALQUILER",
                "30d",
                false,
                0.0,
                0,
                30,
                List.of(new FeatureDriftSummary("area_m2", 0.02, 0.10, false, "PSI"))
        );
        when(dashboardQueryService.handle(any(GetDataDriftQuery.class))).thenReturn(dataDrift);

        mockMvc.perform(get("/api/v1/dashboard/data-drift?operationType=ALQUILER&range=30d"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operation_type").value("ALQUILER"))
                .andExpect(jsonPath("$.drift_detected").value(false))
                .andExpect(jsonPath("$.total_features_count").value(30));
    }

    @Test
    @DisplayName("GET /api/v1/dashboard/concept-drift (Alquiler) - Deriva temporal en alquiler")
    void getConceptDrift_alquiler_success() throws Exception {
        ConceptDriftResult conceptDrift = new ConceptDriftResult("ALQUILER", "30d", false, 0.015, 0.025, 0.018, "Estable");
        when(dashboardQueryService.handle(any(GetConceptDriftQuery.class))).thenReturn(conceptDrift);

        mockMvc.perform(get("/api/v1/dashboard/concept-drift?operationType=ALQUILER&range=30d"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operation_type").value("ALQUILER"))
                .andExpect(jsonPath("$.psi_score").value(0.025))
                .andExpect(jsonPath("$.drift_detected").value(false));
    }

    @Test
    @DisplayName("GET /api/v1/dashboard/latency (Alquiler) - Percentiles de latencia y SLA en alquiler")
    void getLatency_alquiler_success() throws Exception {
        when(dashboardQueryService.getLatencyMetrics(OperationType.ALQUILER)).thenReturn(Map.of(
                "operation_type", "alquiler",
                "p50_latency_ms", 38.5,
                "p95_latency_ms", 44.1,
                "meets_sla", true
        ));

        mockMvc.perform(get("/api/v1/dashboard/latency?operationType=ALQUILER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operation_type").value("alquiler"))
                .andExpect(jsonPath("$.p50_latency_ms").value(38.5))
                .andExpect(jsonPath("$.p95_latency_ms").value(44.1))
                .andExpect(jsonPath("$.meets_sla").value(true));
    }

    @Test
    @DisplayName("GET /api/v1/dashboard/resources - Telemetría de hardware en vivo")
    void getResources_success() throws Exception {
        when(dashboardQueryService.getSystemResources()).thenReturn(Map.of(
                "cpu_usage_pct", 1.5,
                "memory_used_mb", 450.0,
                "memory_total_mb", 1213.0,
                "memory_usage_pct", 37.1
        ));

        mockMvc.perform(get("/api/v1/dashboard/resources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cpu_usage_pct").value(1.5))
                .andExpect(jsonPath("$.memory_used_mb").value(450.0))
                .andExpect(jsonPath("$.memory_total_mb").value(1213.0));
    }
}
