package pe.ventage.valuo_monitoring_service.infrastructure.clients.ml;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pe.ventage.valuo_monitoring_service.application.internal.outboundservices.ExternalMlService;
import pe.ventage.valuo_monitoring_service.domain.model.queries.*;
import pe.ventage.valuo_monitoring_service.infrastructure.clients.ml.dto.*;

import java.util.*;

@Component
@Slf4j
public class MlFeignClientAdapter implements ExternalMlService {

    private final MlFeignClient feignClient;

    public MlFeignClientAdapter(MlFeignClient feignClient) {
        this.feignClient = feignClient;
    }

    @Override
    public MlJobTriggerResponse triggerTraining(MlTriggerTrainingParams params) {
        String tipo = params.operationType().toLowerCase();
        String modelName = "alquiler".equalsIgnoreCase(tipo) ? "xgboost_alquiler_v1" : "xgboost_venta_v2";

        FastApiTriggerTrainingRequest req = new FastApiTriggerTrainingRequest(
                modelName,
                params.guardarComoActivo(),
                params.maxDepth(),
                params.learningRate()
        );

        FastApiTrainingTriggerResponse res;
        if ("alquiler".equalsIgnoreCase(tipo)) {
            res = feignClient.triggerTrainingAlquiler(req);
        } else {
            res = feignClient.triggerTrainingVenta(req);
        }

        return new MlJobTriggerResponse(res.jobId(), res.estado(), res.mensaje());
    }

    @Override
    public Optional<MlJobStatusResponse> getJobStatus(String jobId) {
        try {
            FastApiJobStatusResponse res = feignClient.getJobStatus(jobId);
            if (res == null) return Optional.empty();

            String normalizedStatus = "PENDING";
            if ("en_progreso".equalsIgnoreCase(res.estado())) {
                normalizedStatus = "RUNNING";
            } else if ("completado".equalsIgnoreCase(res.estado())) {
                normalizedStatus = "SUCCESS";
            } else if ("fallido".equalsIgnoreCase(res.estado())) {
                normalizedStatus = "FAILED";
            }

            Double r2 = extractDouble(res.metricas(), "r2");
            Double mae = extractDouble(res.metricas(), "mae");
            Double mape = extractDouble(res.metricas(), "mape_pct");
            if (mape == null) mape = extractDouble(res.metricas(), "mape");

            String modelVersion = res.mlflowModelVersion() != null
                    ? res.mlflowModelVersion()
                    : res.nombreModelo();

            return Optional.of(new MlJobStatusResponse(
                    res.jobId(),
                    normalizedStatus,
                    modelVersion,
                    r2,
                    mae,
                    mape,
                    res.error()
            ));
        } catch (MlServiceException e) {
            if (e.getStatus() == 404) return Optional.empty();
            throw e;
        }
    }

    @Override
    public MlPromoteResponse promoteModel(MlPromoteParams params) {
        FastApiPromoteRequest req = new FastApiPromoteRequest(
                params.operationType().toLowerCase(),
                params.modelVersion(),
                params.reason()
        );
        FastApiActionResponse res = feignClient.promoteModel(req);
        boolean success = Boolean.TRUE.equals(res.exito()) || res.versionPromovida() != null || "Production".equalsIgnoreCase(res.nuevoStage());

        Map<String, Object> deltas = res.deltas();
        Double deltaMape = deltas != null ? extractDouble(deltas, "delta_mape_pct") : null;
        Double deltaR2 = deltas != null ? extractDouble(deltas, "delta_r2") : null;
        Double deltaMae = deltas != null ? extractDouble(deltas, "delta_mae") : null;
        Double deltaRmse = deltas != null ? extractDouble(deltas, "delta_rmse") : null;
        Boolean mejoraMape = deltas != null ? extractBoolean(deltas, "mejora_mape", false) : null;

        String msg = res.mensaje() != null ? res.mensaje() : "Promoción ejecutada exitosamente";

        return new MlPromoteResponse(
                success,
                res.versionPromovida(),
                res.versionAnteriorArchivada(),
                res.nuevoStage() != null ? res.nuevoStage() : "Production",
                msg,
                deltaMape,
                deltaR2,
                deltaMae,
                deltaRmse,
                mejoraMape
        );
    }

    @Override
    public MlRollbackResponse rollbackModel(MlRollbackParams params) {
        FastApiRollbackRequest req = new FastApiRollbackRequest(
                params.operationType().toLowerCase(),
                params.targetVersion(),
                params.reason()
        );
        FastApiActionResponse res = feignClient.rollbackModel(req);
        boolean success = Boolean.TRUE.equals(res.exito()) || res.versionPromovida() != null || "Production".equalsIgnoreCase(res.nuevoStage());

        Map<String, Object> deltas = res.deltas();
        Double deltaMape = deltas != null ? extractDouble(deltas, "delta_mape_pct") : null;
        Double deltaR2 = deltas != null ? extractDouble(deltas, "delta_r2") : null;
        Double deltaMae = deltas != null ? extractDouble(deltas, "delta_mae") : null;
        Double deltaRmse = deltas != null ? extractDouble(deltas, "delta_rmse") : null;
        Boolean mejoraMape = deltas != null ? extractBoolean(deltas, "mejora_mape", false) : null;

        String msg = res.mensaje() != null ? res.mensaje() : "Rollback ejecutado exitosamente";

        return new MlRollbackResponse(
                success,
                res.versionPromovida() != null ? res.versionPromovida() : params.targetVersion(),
                res.versionAnteriorArchivada(),
                msg,
                deltaMape,
                deltaR2,
                deltaMae,
                deltaRmse,
                mejoraMape
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public MlHealthMetricsResponse getHealthMetrics(String operationType) {
        try {
            Map<String, Object> map = feignClient.getModelInfo(operationType.toLowerCase());
            Map<String, Object> metrics = map.get("metricas") instanceof Map<?, ?> m
                    ? (Map<String, Object>) m : Map.of();

            Double r2 = extractDouble(metrics, "r2");
            Double mae = extractDouble(metrics, "mae_soles");
            if (mae == null) mae = extractDouble(metrics, "mae");
            Double mape = extractDouble(metrics, "mape_pct");
            if (mape == null) mape = extractDouble(metrics, "mape");

            String version = extractString(map, "version", "v1.0.0");
            String status = (r2 != null && r2 >= 0.70 && mape != null && mape <= 15.0) ? "HEALTHY" : "DEGRADED";

            return new MlHealthMetricsResponse(
                    operationType,
                    version,
                    r2,
                    mae,
                    mape,
                    status
            );
        } catch (Exception e) {
            log.warn("Failed to get model info from ML service: {}", e.getMessage());
            return new MlHealthMetricsResponse(operationType, "unknown", 0.0, 0.0, 0.0, "UNKNOWN");
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public MlDriftStatusResponse getDriftStatus(String operationType) {
        try {
            Map<String, Object> map = feignClient.getDataDrift(operationType.toLowerCase());
            String driftStatus = extractString(map, "drift_status", "STABLE");
            boolean dataDriftDetected = !"STABLE".equalsIgnoreCase(driftStatus);
            Double overallPsi = extractDouble(map, "overall_psi");

            List<Map<String, Object>> features = map.get("features") instanceof List<?> list
                    ? (List<Map<String, Object>>) list : List.of();

            int totalFeatures = features.size();
            int driftedFeatures = 0;
            for (Map<String, Object> f : features) {
                String st = (String) f.get("status");
                if ("MODERATE_DRIFT".equalsIgnoreCase(st) || "SIGNIFICANT_DRIFT".equalsIgnoreCase(st)) {
                    driftedFeatures++;
                }
            }

            Double share = totalFeatures > 0 ? (double) driftedFeatures / totalFeatures : 0.0;

            return new MlDriftStatusResponse(
                    operationType,
                    dataDriftDetected,
                    false,
                    share,
                    driftedFeatures,
                    totalFeatures,
                    null,
                    overallPsi
            );
        } catch (Exception e) {
            log.warn("Failed to get drift status from ML service: {}", e.getMessage());
            return new MlDriftStatusResponse(operationType, false, false, 0.0, 0, 0, 0.0, 0.0);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public MlDataDriftDetailResponse getDataDrift(String operationType, String temporalRange) {
        try {
            Map<String, Object> map = feignClient.getDataDrift(operationType.toLowerCase());
            String driftStatus = extractString(map, "drift_status", "STABLE");
            boolean dataDriftDetected = !"STABLE".equalsIgnoreCase(driftStatus);

            List<FeatureDriftSummary> metrics = new ArrayList<>();
            List<Map<String, Object>> features = map.get("features") instanceof List<?> list
                    ? (List<Map<String, Object>>) list : List.of();

            int driftedFeatures = 0;
            for (Map<String, Object> f : features) {
                String st = (String) f.get("status");
                boolean isDrifted = "MODERATE_DRIFT".equalsIgnoreCase(st) || "SIGNIFICANT_DRIFT".equalsIgnoreCase(st);
                if (isDrifted) driftedFeatures++;

                metrics.add(new FeatureDriftSummary(
                        (String) f.get("feature_name"),
                        toDouble(f.get("psi_score")),
                        0.10,
                        isDrifted,
                        "PSI"
                ));
            }

            Double share = !features.isEmpty() ? (double) driftedFeatures / features.size() : 0.0;

            return new MlDataDriftDetailResponse(
                    operationType,
                    extractString(map, "target_period", temporalRange),
                    dataDriftDetected,
                    share,
                    driftedFeatures,
                    features.size(),
                    metrics
            );
        } catch (Exception e) {
            log.warn("Failed to get data drift from ML service: {}", e.getMessage());
            return new MlDataDriftDetailResponse(operationType, temporalRange, false, 0.0, 0, 0, List.of());
        }
    }

    @Override
    public MlConceptDriftDetailResponse getConceptDrift(String operationType, String temporalRange) {
        try {
            Map<String, Object> map = feignClient.getFluctuacionTemporal(operationType.toLowerCase());
            Double overallPsi = extractDouble(map, "overall_psi");
            String interp = extractString(map, "message", "Evaluación temporal completada");

            return new MlConceptDriftDetailResponse(
                    operationType,
                    temporalRange,
                    false,
                    null,
                    overallPsi,
                    null,
                    interp
            );
        } catch (Exception e) {
            log.warn("Failed to get concept drift / fluctuacion temporal from ML service: {}", e.getMessage());
            return new MlConceptDriftDetailResponse(operationType, temporalRange, false, 0.0, 0.0, null, "Sin datos remotos");
        }
    }

    @Override
    public Optional<MlActiveModelInfo> getActiveModel(String operationType) {
        try {
            Map<String, Object> map = feignClient.getModelInfo(operationType.toLowerCase());
            if (map == null || map.isEmpty()) return Optional.empty();

            return Optional.of(new MlActiveModelInfo(
                    extractString(map, "version", "unknown"),
                    "PRODUCTION",
                    operationType
            ));
        } catch (Exception e) {
            log.warn("Failed to get active model from ML service: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public ModelBenchmarkResult getBenchmark(String operationType, boolean soloUltimo) {
        try {
            Map<String, Object> map = feignClient.getBenchmark(operationType.toLowerCase(), soloUltimo);
            Double benchmarkRefMape = extractDouble(map, "benchmark_referencial_mape");
            if (benchmarkRefMape == null) benchmarkRefMape = 15.0;

            Map<String, Object> prodMap = map.get("modelo_produccion") instanceof Map<?, ?> m
                    ? (Map<String, Object>) m : Map.of();
            ProductionModelSummary prodSummary = parseProductionSummary(prodMap);

            List<CandidateBenchmarkSummary> candidates = new ArrayList<>();
            Object candObj = map.get("candidatos");
            if (candObj instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> cMap) {
                        candidates.add(parseCandidateSummary((Map<String, Object>) cMap));
                    }
                }
            }

            Integer totalCandidatos = extractInteger(map, "total_candidatos", candidates.size());
            String generadoEn = extractString(map, "generado_en", "");

            return new ModelBenchmarkResult(
                    operationType,
                    benchmarkRefMape,
                    prodSummary,
                    candidates,
                    totalCandidatos,
                    generadoEn
            );
        } catch (Exception e) {
            log.error("Failed to get benchmark from ML service: {}", e.getMessage(), e);
            throw new MlServiceException(503, "No se pudo obtener el benchmark de modelos desde el servicio ML: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private ProductionModelSummary parseProductionSummary(Map<String, Object> map) {
        if (map == null || map.isEmpty()) return null;
        Map<String, Object> metricsMap = map.get("metricas") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        return new ProductionModelSummary(
                extractString(map, "nombre_modelo", "xgboost"),
                extractString(map, "version", ""),
                extractString(map, "stage", "Production"),
                extractString(map, "run_id", ""),
                parseMetrics(metricsMap),
                extractString(map, "creado_en", ""),
                extractString(map, "fuente_artefacto", "")
        );
    }

    @SuppressWarnings("unchecked")
    private CandidateBenchmarkSummary parseCandidateSummary(Map<String, Object> map) {
        if (map == null || map.isEmpty()) return null;
        Map<String, Object> candMap = map.get("candidato") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        Map<String, Object> metricsMap = candMap.get("metricas") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        CandidateModelSummary candSummary = new CandidateModelSummary(
                extractString(candMap, "nombre_modelo", "xgboost"),
                extractString(candMap, "version", ""),
                extractString(candMap, "stage", "Candidate"),
                extractString(candMap, "run_id", ""),
                parseMetrics(metricsMap),
                extractString(candMap, "creado_en", ""),
                extractString(candMap, "fuente_artefacto", "")
        );

        Map<String, Object> deltasMap = map.get("deltas") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        BenchmarkDeltas deltas = new BenchmarkDeltas(
                extractDouble(deltasMap, "delta_mape_pct"),
                extractDouble(deltasMap, "delta_r2"),
                extractDouble(deltasMap, "delta_mae"),
                extractDouble(deltasMap, "delta_rmse"),
                extractBoolean(deltasMap, "mejora_mape", false),
                extractBoolean(deltasMap, "mejora_r2", false)
        );

        return new CandidateBenchmarkSummary(
                candSummary,
                deltas,
                extractString(map, "recomendacion", ""),
                extractString(map, "motivo", "")
        );
    }

    private BenchmarkMetrics parseMetrics(Map<String, Object> m) {
        if (m == null || m.isEmpty()) return null;
        Double mape = extractDouble(m, "mape_pct");
        if (mape == null) mape = extractDouble(m, "mape");
        return new BenchmarkMetrics(
                mape,
                extractDouble(m, "r2"),
                extractDouble(m, "mae"),
                extractDouble(m, "rmse"),
                extractInteger(m, "n_train", null),
                extractInteger(m, "n_test", null)
        );
    }

    private Double extractDouble(Map<String, Object> map, String key) {
        if (map == null) return null;
        return toDouble(map.get(key));
    }

    private Double toDouble(Object val) {
        if (val instanceof Number n) return n.doubleValue();
        if (val instanceof String s) {
            try { return Double.parseDouble(s); } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private String extractString(Map<String, Object> map, String key, String defaultVal) {
        if (map == null || !map.containsKey(key)) return defaultVal;
        Object val = map.get(key);
        return val != null ? val.toString() : defaultVal;
    }

    private Integer extractInteger(Map<String, Object> map, String key, Integer defaultVal) {
        if (map == null || !map.containsKey(key)) return defaultVal;
        Object val = map.get(key);
        return val instanceof Number n ? n.intValue() : defaultVal;
    }

    private boolean extractBoolean(Map<String, Object> map, String key, boolean defaultVal) {
        if (map == null || !map.containsKey(key)) return defaultVal;
        Object val = map.get(key);
        return val instanceof Boolean b ? b : defaultVal;
    }
}
