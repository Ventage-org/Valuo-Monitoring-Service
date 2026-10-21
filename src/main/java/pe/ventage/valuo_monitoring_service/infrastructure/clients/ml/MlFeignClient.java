package pe.ventage.valuo_monitoring_service.infrastructure.clients.ml;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import pe.ventage.valuo_monitoring_service.infrastructure.clients.ml.dto.*;

import java.util.Map;

@FeignClient(
        name = "ml-service",
        url = "${ml.service.url:http://localhost:8000}",
        configuration = MlFeignConfig.class
)
public interface MlFeignClient {

    // 1. Reentrenamiento Asíncrono
    @PostMapping("/api/v1/admin/entrenamiento/venta")
    FastApiTrainingTriggerResponse triggerTrainingVenta(@RequestBody FastApiTriggerTrainingRequest request);

    @PostMapping("/api/v1/admin/entrenamiento/alquiler")
    FastApiTrainingTriggerResponse triggerTrainingAlquiler(@RequestBody FastApiTriggerTrainingRequest request);

    @GetMapping("/api/v1/admin/entrenamiento/estado/{job_id}")
    FastApiJobStatusResponse getJobStatus(@PathVariable("job_id") String jobId);

    @GetMapping("/api/v1/admin/entrenamiento/estado")
    Map<String, Object> getLatestJobStatus();

    @GetMapping("/api/v1/admin/entrenamiento/jobs")
    Object listTrainingJobs();

    // 2. Modelos y Gobernanza
    @GetMapping("/api/v1/admin/modelos/benchmark/{tipo_operacion}")
    Map<String, Object> getBenchmark(
            @PathVariable("tipo_operacion") String tipoOperacion,
            @RequestParam(value = "solo_ultimo", defaultValue = "true") boolean soloUltimo
    );

    @GetMapping("/api/v1/admin/modelos/historial/{tipo_operacion}")
    Map<String, Object> getModelHistory(@PathVariable("tipo_operacion") String tipoOperacion);

    @PostMapping("/api/v1/admin/modelos/promover")
    FastApiActionResponse promoteModel(@RequestBody FastApiPromoteRequest request);

    @PostMapping("/api/v1/admin/modelos/rollback")
    FastApiActionResponse rollbackModel(@RequestBody FastApiRollbackRequest request);

    // 3. Monitoreo y Observabilidad
    @GetMapping("/api/v1/admin/monitoreo/drift/{tipo_operacion}")
    Map<String, Object> getDataDrift(@PathVariable("tipo_operacion") String tipoOperacion);

    @GetMapping("/api/v1/admin/monitoreo/fluctuacion-temporal/{tipo_operacion}")
    Map<String, Object> getFluctuacionTemporal(@PathVariable("tipo_operacion") String tipoOperacion);

    @GetMapping("/api/v1/admin/monitoreo/latencia-p95/{tipo_operacion}")
    Map<String, Object> getLatenciaP95(@PathVariable("tipo_operacion") String tipoOperacion);

    @GetMapping("/api/v1/admin/monitoreo/recursos")
    Map<String, Object> getRecursos();

    @GetMapping("/api/v1/modelo/info")
    Map<String, Object> getModelInfo(@RequestParam("tipo_operacion") String tipoOperacion);

    @GetMapping("/health")
    Map<String, Object> getHealth();
}
