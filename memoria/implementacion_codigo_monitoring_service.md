# 4.3 Implementación Aplicación / Solución (Código)

## Backend de Monitoreo y Gobernanza MLOps: Microservicio de Monitoreo (`valuo-monitoring-service`)

El microservicio `valuo-monitoring-service` es el componente responsable de la observabilidad continua, gobernanza y trazabilidad del ciclo de vida de los modelos predictivos (MLOps) en la plataforma Valuo. Desarrollado en **Java 21 con Spring Boot 3**, implementa **Domain-Driven Design (DDD) táctico** y el patrón **CQRS**. Su objetivo es supervisar la degradación y deriva estadística (*Data Drift* y *Concept Drift*), garantizar un registro inmutable de auditoría para cada transición de modelo, y desacoplar la ejecución asíncrona de reentrenamientos mediante tareas en segundo plano.

A continuación, se presenta la estructura simplificada del módulo y las líneas de código más representativas:

```
valuo-monitoring-service/
└── src/main/java/pe/ventage/valuo_monitoring_service/
    ├── domain/
    │   ├── model/aggregates/
    │   │   ├── ModelTransitionLog.java           # Bitácora inmutable de auditoría (append-only)
    │   │   └── ModelTrainingJob.java             # Estado y ciclo de vida de jobs de entrenamiento
    │   └── services/                             # Interfaces de servicios de dominio (CQRS)
    ├── application/internal/
    │   ├── commandservices/
    │   │   └── GovernanceCommandServiceImpl.java # Mutex de entrenamiento y transiciones de modelo
    │   └── queryservices/
    │       └── DashboardQueryServiceImpl.java    # Agregación de drift, telemetría y salud
    ├── infrastructure/
    │   └── scheduler/
    │       └── TrainingJobPollerScheduler.java   # Poller asíncrono en background (@Scheduled)
    └── interfaces/rest/
        ├── GovernanceController.java             # Endpoints de ciclo de vida de modelos e IPC en caliente
        └── DashboardController.java              # Endpoints de observabilidad, telemetría y drift
```

---

### 1. Agregado Raíz de Auditoría y Trazabilidad Append-Only (`ModelTransitionLog.java`)

Modela una bitácora inmutable de solo inserción (*append-only*) en el esquema `monitoring`. Cada cambio de estado de un modelo (promoción, reversión o reentrenamiento) se registra mediante *factory methods* especializados, calculando automáticamente un resumen textual del diferencial de métricas ($\Delta \text{MAPE}$, $\Delta R^2$, $\Delta \text{MAE}$) para facilitar el peritaje técnico:

```java
// domain/model/aggregates/ModelTransitionLog.java
public static ModelTransitionLog forPromotion(
        OperationType operationType, String newVersion, String previousVersion,
        ModelStage currentStage, String performedBy, String reason,
        Double deltaMapePct, Double deltaR2, Double deltaMae, Double deltaRmse, Boolean mejoraMape
) {
    if (operationType == null) throw new InvalidDomainAttributeException("OperationType cannot be null");
    if (newVersion == null || newVersion.isBlank()) throw new InvalidDomainAttributeException("Model version is required");

    ModelTransitionLog log = new ModelTransitionLog();
    log.modelVersion = newVersion.trim();
    log.previousVersion = previousVersion;
    log.operationType = operationType;
    log.previousStage = currentStage != null ? currentStage : ModelStage.STAGING;
    log.newStage = ModelStage.PRODUCTION;
    log.action = TransitionAction.PROMOTION;
    log.performedBy = performedBy != null ? performedBy : "system";
    log.reason = reason;
    log.status = "SUCCESS";
    log.deltaMapePct = deltaMapePct;
    log.deltaR2 = deltaR2;
    log.mejoraMape = mejoraMape;
    log.metricChangeSummary = buildDeltaSummary(deltaMapePct, deltaR2, deltaMae, mejoraMape);
    log.createdAt = LocalDateTime.now();
    return log;
}

private static String buildDeltaSummary(Double deltaMape, Double deltaR2, Double deltaMae, Boolean mejoraMape) {
    if (deltaMape == null && deltaR2 == null) return "Sin métricas delta registradas";
    StringBuilder sb = new StringBuilder();
    if (deltaMape != null) sb.append(String.format("ΔMAPE: %+.2f%%", deltaMape));
    if (deltaR2 != null) sb.append(String.format(" | ΔR²: %+.4f", deltaR2));
    if (Boolean.TRUE.equals(mejoraMape)) sb.append(" (Mejora: Sí)");
    return sb.toString();
}
```

---

### 2. Control de Concurrencia y Mutex de Reentrenamiento (`GovernanceCommandServiceImpl.java`)

Para evitar saturación de recursos y colisiones en MLflow por ejecuciones simultáneas, este servicio actúa como un semáforo de exclusión mutua (*Mutex*). Valida en dos niveles: descarta tareas activas en la base de datos relacional local (`PENDING` o `RUNNING`) y verifica la disponibilidad remota del servicio analítico antes de disparar el proceso:

```java
// application/internal/commandservices/GovernanceCommandServiceImpl.java
@Override
@Transactional
public ModelTrainingJob handle(TriggerTrainingCommand command) {
    // 1. Verificación local en base de datos
    List<ModelTrainingJob> activeJobs = jobRepository.findByStatusIn(List.of(JobStatus.PENDING, JobStatus.RUNNING));
    if (!activeJobs.isEmpty()) {
        throw new TrainingConflictException("Ya existe un entrenamiento en curso (Job ID: " + activeJobs.get(0).getJobId() + ").");
    }

    // 2. Verificación remota en el motor ML
    if (!externalMlService.isTrainingAvailable()) {
        throw new TrainingConflictException("El servicio ML se encuentra ocupado ejecutando otro entrenamiento.");
    }

    // Política de gobernanza: autoPromote = false (requiere evaluación explícita)
    MlTriggerTrainingParams params = new MlTriggerTrainingParams(
            command.operationType().name().toLowerCase(), command.datasetVersion(),
            command.targetMetric(), false, command.maxDepth(), command.learningRate()
    );

    MlJobTriggerResponse mlResponse = externalMlService.triggerTraining(params);
    ModelTrainingJob job = new ModelTrainingJob(command, mlResponse.jobId());
    ModelTrainingJob savedJob = jobRepository.save(job);

    // Registro inicial en bitácora de auditoría en estado IN_PROGRESS
    ModelTransitionLog auditEntry = ModelTransitionLog.forTraining(
            command.operationType(), "v1.0.0", mlResponse.jobId(), command.triggeredBy(), "Reentrenamiento iniciado"
    );
    transitionLogRepository.save(auditEntry);
    return savedJob;
}
```

---

### 3. Sondeo Asíncrono y Detección de Transición Terminal (`TrainingJobPollerScheduler.java`)

Dado que el reentrenamiento es una tarea pesada (15-45 segundos), el microservicio desacopla el seguimiento del cliente HTTP mediante un poller en segundo plano programado con `@Scheduled(fixedDelay = 5000)`. Cada 5 segundos sondea los jobs activos y, al detectar un estado terminal (`SUCCESS` o `FAILED`), actualiza las métricas y persiste el registro final de auditoría:

```java
// infrastructure/scheduler/TrainingJobPollerScheduler.java
@Component
@Slf4j
public class TrainingJobPollerScheduler {

    @Scheduled(fixedDelay = 5000)
    public void syncPendingJobs() {
        List<ModelTrainingJob> activeJobs = jobRepository.findByStatusIn(List.of(JobStatus.PENDING, JobStatus.RUNNING));
        if (activeJobs.isEmpty()) return;

        for (ModelTrainingJob job : activeJobs) {
            try {
                ModelTrainingJob updated = governanceCommandService.handle(new SyncJobStatusCommand(job.getJobId()));
                if (updated.getStatus().isTerminal()) {
                    log.info("Training job {} finalizó con estado {}", job.getJobId(), updated.getStatus());
                }
            } catch (Exception e) {
                log.warn("Error al sincronizar job {}: {}", job.getJobId(), e.getMessage());
            }
        }
    }
}
```

```java
// application/internal/commandservices/GovernanceCommandServiceImpl.java (fragmento terminal)
if (parsedStatus == JobStatus.SUCCESS) {
    job.complete(mlStatus.modelVersion(), mlStatus.metricsR2(), mlStatus.metricsMae(), mlStatus.metricsMape());
    
    // Registro terminal de auditoría con métricas finales
    ModelTransitionLog auditCompleted = ModelTransitionLog.forTrainingCompleted(
            job.getOperationType(), mlStatus.modelVersion(), activeVersion,
            job.getTriggeredBy(), mlStatus.metricsR2(), mlStatus.metricsMae(), mlStatus.metricsMape()
    );
    transitionLogRepository.save(auditCompleted);
}
```

---

### 4. Actualización del Factor IPC en Caliente y Observabilidad (`GovernanceController.java` y `DashboardController.java`)

Permite reconfigurar el factor deflactador del Índice de Precios al Consumidor (IPC) en memoria RAM sin reiniciar el servicio (*zero-downtime*), y expone la telemetría en tiempo real de recursos y latencia ($P_{50}, P_{95}, P_{99}$ frente al SLA $<100\text{ ms}$):

```java
// interfaces/rest/GovernanceController.java
@PutMapping("/ipc")
@Operation(summary = "Actualizar factor IPC en caliente", description = "Actualiza el factor en RAM sin downtime.")
public ResponseEntity<Map<String, Object>> updateIpcConfig(@Valid @RequestBody UpdateIpcResource resource) {
    Map<String, Object> response = commandService.updateIpcConfig(
            resource.valor(), resource.periodo(), resource.fuente(),
            resource.actualizadoPor() != null ? resource.actualizadoPor() : "admin@valuo.pe",
            resource.nota(), resource.aplicarA()
    );
    return ResponseEntity.ok(response);
}
```

```java
// interfaces/rest/DashboardController.java
@GetMapping("/latency")
@Operation(summary = "Latencia P95 y Hash de Artefactos")
public ResponseEntity<Map<String, Object>> getLatency(@RequestParam(defaultValue = "VENTA") String operationType) {
    return ResponseEntity.ok(dashboardQueryService.getLatencyMetrics(OperationType.fromString(operationType)));
}

@GetMapping("/resources")
@Operation(summary = "Telemetría de recursos del contenedor ML")
public ResponseEntity<Map<String, Object>> getResources() {
    return ResponseEntity.ok(dashboardQueryService.getSystemResources());
}
```
