# Valuo Monitoring Service - Módulo de Gobernanza y Auditoría de Modelos ML

Este documento detalla la arquitectura, endpoints y cambios recientes implementados en el servicio de monitoreo y gobernanza (`valuo-monitoring-service`), integrando el ciclo de vida de modelos de Machine Learning (FastAPI / MLflow) y la persistencia de trazabilidad en PostgreSQL.

---

## 1. Tabla de Auditoría y Trazabilidad (`/api/v1/audit/logs`)

El endpoint `GET /api/v1/audit/logs` expone la trazabilidad completa del ciclo de vida de los modelos para su visualización tabular en el Frontend.

### Mapeo de Columnas: Frontend vs Backend

| Columna Frontend | Campo JSON | Tipo de Dato | Valores / Descripción |
|---|---|---|---|
| **Fecha y Hora** | `created_at` / `fecha_hora` | `String (ISO 8601)` | Timestamp del evento en auditoría |
| **Acción** | `action` / `accion` | `String` | `PROMOTION`, `ROLLBACK`, `TRAINING` |
| **Operación** | `operation_type` / `operacion` | `String` | `VENTA`, `ALQUILER` |
| **Versión Anterior** | `previous_version` / `version_anterior` | `String` | Versión que estaba en Producción (o modelo base) |
| **Nueva Versión** | `new_version` / `nueva_version` | `String` | Versión promovida, restaurada o identificador del job |
| **Motivo / Justificación** | `reason` / `motivo` | `String` | Justificación ingresada por el administrador |
| **Usuario** | `performed_by` / `usuario` | `String` | Administrador que ejecutó la acción (`admin@valuo.pe`) |
| **Estado** | `status` / `estado` | `String` | `SUCCESS`, `IN_PROGRESS`, `FAILED` |
| **Cambio de Métrica** | `cambio_metrica` / `deltas` | `String` / `Object` | Resumen formateado (`ΔMAPE`, `ΔR²`, `ΔMAE`) y bloque numérico estructurado |

### Estructura de Respuesta JSON

```json
{
  "content": [
    {
      "id": 9,
      "created_at": "2026-10-12T21:20:04.480423",
      "action": "TRAINING",
      "operation_type": "VENTA",
      "previous_version": "v10_mlflow",
      "new_version": "xgboost_venta_v2",
      "reason": "Entrenamiento completado y registrado en MLflow",
      "performed_by": "admin@valuo.pe",
      "status": "SUCCESS",
      "cambio_metrica": "Métricas: MAPE=14.16% | R²=0.7472 | MAE=50897.93",
      "deltas": null,
      "previous_stage": "PRODUCTION",
      "new_stage": "CANDIDATE"
    },
    {
      "id": 6,
      "created_at": "2026-10-12T21:02:54.921280",
      "action": "PROMOTION",
      "operation_type": "VENTA",
      "previous_version": "11",
      "new_version": "10",
      "reason": "Mejora continua tras validación de benchmark",
      "performed_by": "admin@valuo.pe",
      "status": "SUCCESS",
      "cambio_metrica": "ΔMAPE: -0.83% | ΔR²: +0.0001 | ΔMAE: -1223.45 (Mejora: Sí)",
      "deltas": {
        "delta_mape_pct": -0.8274,
        "delta_r2": 0.0001,
        "delta_mae": -1223.45,
        "delta_rmse": 10.26,
        "mejora_mape": true,
        "mejora_r2": true
      },
      "previous_stage": "PRODUCTION",
      "new_stage": "PRODUCTION"
    }
  ],
  "page": 0,
  "size": 20,
  "total_elements": 2,
  "total_pages": 1,
  "is_last": true
}
```

---

## 2. Ciclo de Vida Append-Only para Reentrenamiento

Para respetar los estándares de inmutabilidad en auditoría:

```
[ POST /api/v1/governance/training ]
                │
                ▼
1. Inserta Evento T0 (Disparo)
   - action: TRAINING
   - status: IN_PROGRESS
   - new_version: Job: <uuid>
   - reason: "Job iniciado en background"
                │
                ▼
2. TrainingJobPollerScheduler (@Scheduled cada 5s)
   - Consulta estado del job en FastAPI
                │
                ▼
3. FastAPI finaliza entrenamiento en MLflow
                │
                ▼
4. Inserta Evento T1 (Culminación)
   - action: TRAINING
   - status: SUCCESS (o FAILED)
   - new_version: <versión registrada en MLflow>
   - reason: "Entrenamiento completado y registrado en MLflow"
   - cambio_metrica: Métricas finales calculadas (MAPE, R², MAE)
```

---

## 3. Endpoints de Gobernanza Implementados

### 3.1. Promover Modelo Candidato a Producción
- **Método y Ruta:** `POST /api/v1/governance/promote`
- **Request Body:**
  ```json
  {
    "operation_type": "VENTA",
    "reason": "Aprobado por comité tras validar benchmark"
  }
  ```
- **Comportamiento:**
  - FastAPI descarga el modelo candidato desde MLflow, lo carga en memoria RAM, inicializa el explicador SHAP y archiva la versión anterior.
  - Retorna y persiste en auditoría los **deltas de métricas** respecto al modelo archivado.

### 3.2. Rollback a Versión Anterior
- **Método y Ruta:** `POST /api/v1/governance/rollback`
- **Request Body:**
  ```json
  {
    "operation_type": "VENTA",
    "target_version": "8",
    "reason": "Degradación detectada en producción"
  }
  ```

### 3.3. Benchmark Comparativo (Producción vs Candidatos)
- **Método y Ruta:** `GET /api/v1/governance/benchmark?operationType=VENTA`
- **Comportamiento:** Consulta las métricas del modelo activo en producción y las compara contra el último candidato disponible en MLflow con `solo_ultimo=true`.

### 3.4. Disparar Reentrenamiento Manual
- **Método y Ruta:** `POST /api/v1/governance/training`
- **Request Body:**
  ```json
  {
    "operation_type": "VENTA",
    "max_depth": 6,
    "learning_rate": 0.04
  }
  ```
  *(Parámetros `max_depth` y `learning_rate` opcionales; si no se envían, FastAPI usa valores predeterminados).*

---

## 4. Cambios y Archivos Clave Modificados

1. **[ModelTransitionLog.java](file:///d:/NuevaCarpetaLool/proyecto-tesis/valuo-monitoring-service/src/main/java/pe/ventage/valuo_monitoring_service/domain/model/aggregates/ModelTransitionLog.java)**:
   - Nuevos campos para deltas de métricas (`deltaMapePct`, `deltaR2`, `deltaMae`, `deltaRmse`, `mejoraMape`), `previousVersion`, `status` y `metricChangeSummary`.
   - Fábricas inmutables: `forPromotion`, `forRollback`, `forTraining`, `forTrainingCompleted`, `forTrainingFailed`.

2. **[ModelTransitionLogResponseResource.java](file:///d:/NuevaCarpetaLool/proyecto-tesis/valuo-monitoring-service/src/main/java/pe/ventage/valuo_monitoring_service/interfaces/rest/resources/ModelTransitionLogResponseResource.java)**:
   - DTO con serialización de nombres en español e inglés para mapeo directo con las columnas requeridas en el Frontend.

3. **[TrainingJobPollerScheduler.java](file:///d:/NuevaCarpetaLool/proyecto-tesis/valuo-monitoring-service/src/main/java/pe/ventage/valuo_monitoring_service/infrastructure/scheduler/TrainingJobPollerScheduler.java)**:
   - Tarea programada `@Scheduled(fixedDelay = 5000)` que sondea jobs activos (`PENDING`, `RUNNING`) y registra automáticamente el log de finalización (`SUCCESS` o `FAILED`).

4. **[ValuoMonitoringServiceApplication.java](file:///d:/NuevaCarpetaLool/proyecto-tesis/valuo-monitoring-service/src/main/java/pe/ventage/valuo_monitoring_service/ValuoMonitoringServiceApplication.java)**:
   - Activación de `@EnableScheduling` para habilitar tareas programadas en Spring Boot.

5. **[MlFeignConfig.java](file:///d:/NuevaCarpetaLool/proyecto-tesis/valuo-monitoring-service/src/main/java/pe/ventage/valuo_monitoring_service/infrastructure/clients/ml/MlFeignConfig.java)**:
   - Configuración de `feign.Request.Options` con timeout de lectura de 60 segundos para evitar errores `Read timed out` durante la carga de artefactos y cálculo de SHAP en FastAPI.

6. **[GovernanceCommandServiceImpl.java](file:///d:/NuevaCarpetaLool/proyecto-tesis/valuo-monitoring-service/src/main/java/pe/ventage/valuo_monitoring_service/application/internal/commandservices/GovernanceCommandServiceImpl.java)**:
   - Integración completa del registro de auditoría en comandos de reentrenamiento, sincronización, promoción y rollback.
