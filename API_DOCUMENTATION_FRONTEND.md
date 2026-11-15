# Guía Completa de Integración REST API para Frontend: Módulo de Monitoreo y Gobernanza MLOps

**Microservicio:** `valuo-monitoring-service`  
**Tecnología Backend:** Java 21 / Spring Boot 3 (Arquitectura Hexagonal DDD + CQRS)  
**Versión de API:** `v1`  
**Base URL Local:** `http://localhost:8082`  
**Base URL Producción:** Variable de entorno del Gateway / Container Apps (ej. `https://monitoring.valuo.pe`)  
**Fecha de Publicación:** Noviembre 2026  

---

## 1. Convenciones y Principios para el Frontend

### 1.1. Prefijos de Ruta
Todos los endpoints del microservicio se agrupan en tres controladores especializados:
- `/api/v1/dashboard`: Vista de observabilidad, telemetría de hardware, latencia, deriva (*drift*) y salud de modelos.
- `/api/v1/governance`: Ciclo de vida de modelos (reentrenamiento, estado de jobs, benchmark, promoción, rollback, descarte y factor macroeconómico IPC).
- `/api/v1/audit`: Bitácora inmutable de auditoría pericial y trazabilidad de eventos.

### 1.2. Parámetro `operationType`
En todos los endpoints que admiten el tipo de operación, se soportan dos valores en mayúsculas:
- `"VENTA"`: Modelo predictivo de tasación de compraventa comercial.
- `"ALQUILER"`: Modelo predictivo de canon de arrendamiento mensual.
*(El valor por defecto en los endpoints es siempre `"VENTA"` si se omite).*

### 1.3. Formato Estándar de Errores (RFC 7807 - Problem Details)
### 1.3. Soporte CORS (Cross-Origin Resource Sharing)
El microservicio tiene **CORS habilitado por defecto** mediante un `CorsFilter` que intercepta todas las peticiones (incluyendo preflight `OPTIONS`):
- **Orígenes autorizados por defecto:** `http://localhost:4200` (Angular), `http://localhost:3000` (React/Next.js) y `http://localhost:5173` (Vite).
- **Configurable por variable de entorno:** `CORS_ALLOWED_ORIGINS` (para producción).
- **Métodos permitidos:** `GET`, `POST`, `PUT`, `DELETE`, `PATCH`, `OPTIONS`.
- **Credenciales y Cabeceras:** `allowCredentials(true)`, cabeceras libres (`*`) y caché de preflight de 1 hora (`maxAge: 3600`).

### 1.4. Formato Estándar de Errores (RFC 7807 - Problem Details)
Todas las respuestas de error retornan la estructura estándar `application/problem+json`:
```json
{
  "type": "https://valuo.pe/errors/training-conflict",
  "title": "Training Conflict",
  "status": 409,
  "detail": "Ya existe un entrenamiento en curso (Job ID: job-123). Solo se permite un job simultáneo.",
  "timestamp": "2026-11-08T15:00:00Z"
}
```

---

## 2. Mapa de Pantallas del Frontend vs Endpoints

| Pantalla / Vista en Frontend | Endpoints Consumidos | Propósito Principal |
| :--- | :--- | :--- |
| **1. Panel General (Dashboard Overview)** | `GET /api/v1/dashboard/overview`<br>`GET /api/v1/dashboard/resources` | Tarjetas de KPI principales, semáforo global de salud y consumo de CPU/RAM del contenedor. |
| **2. Ficha Técnica y Salud del Modelo** | `GET /api/v1/dashboard/health`<br>`GET /api/v1/dashboard/model-info` | Versión activa en producción, periodos de entrenamiento/test, 33 variables predictoras y distritos soportados. |
| **3. Análisis de Deriva (Drift) y Temporal** | `GET /api/v1/dashboard/data-drift`<br>`GET /api/v1/dashboard/concept-drift`<br>`GET /api/v1/dashboard/temporal-fluctuation` | Barras de PSI por feature, métricas de deriva de target (Wasserstein, KS) y gráfica de línea temporal trimestral frente al 15% de MAPE. |
| **4. Telemetría de Latencia y SLA** | `GET /api/v1/dashboard/latency` | Indicadores de latencia P50, P95, P99 contra el SLA de 100ms e integridad SHA-256 de los pesos. |
| **5. Comparativa Benchmark y Decisiones** | `GET /api/v1/governance/benchmark`<br>`POST /api/v1/governance/promote`<br>`POST /api/v1/governance/discard` | Tabla de decisión humana: `@champion` en producción vs `candidatos`, con botones para **Promover** o **Descartar**. |
| **6. Reentrenamiento y Estado de Jobs** | `GET /api/v1/governance/training/availability`<br>`POST /api/v1/governance/training`<br>`GET /api/v1/governance/jobs/{jobId}`<br>`GET /api/v1/governance/jobs` | Botón "Lanzar Reentrenamiento" con validación de semáforo mutex, barra de progreso asíncrona y tabla histórica de jobs. |
| **7. Reversión de Emergencia (Rollback)** | `GET /api/v1/governance/models/history`<br>`POST /api/v1/governance/rollback` | Selector de versiones archivadas en MLflow para restaurar a producción con Hot-Reload inmediato. |
| **8. Bitácora de Auditoría Pericial** | `GET /api/v1/audit/logs` | Tabla paginada de auditoría con filtros por fecha, tipo de operación y acción (`PROMOTION`, `ROLLBACK`, `TRAINING`, `DISCARD`). |
| **9. Configuración Macroeconómica IPC** | `GET /api/v1/governance/ipc`<br>`PUT /api/v1/governance/ipc` | Consulta y actualización trimestral en caliente del factor deflactador IPC sin caída de servicio. |

---

## 3. Especificación Detallada: Módulo Dashboard y Observabilidad

### 3.1. Resumen Ejecutivo Consolidado
- **Método / Ruta:** `GET /api/v1/dashboard/overview`
- **Query Params:**
  - `operationType` (string, opcional, default: `"VENTA"`): `"VENTA"` o `"ALQUILER"`.
- **Descripción:** Consolida en un solo request la salud del modelo, data drift, concept drift y genera un semáforo global con las alertas activas para el encabezado del dashboard.
- **Respuesta (HTTP 200 OK):**
```json
{
  "health": {
    "operation_type": "VENTA",
    "active_model_version": "v1_mlflow",
    "r2": 0.7491,
    "mae": 51533.21,
    "mape": 14.7181,
    "status": "HEALTHY",
    "last_evaluated": "2026-11-08T15:20:00"
  },
  "data_drift": {
    "operation_type": "VENTA",
    "temporal_range": "30d",
    "drift_detected": false,
    "share_of_drifted_features": 0.0,
    "drifted_features_count": 0,
    "total_features_count": 33,
    "metrics": []
  },
  "concept_drift": {
    "operation_type": "VENTA",
    "temporal_range": "30d",
    "drift_detected": false,
    "wasserstein_distance": 0.034,
    "psi_score": 0.048,
    "ks_statistic": 0.021,
    "interpretation": "Distribución del target estable sin deriva significativa"
  },
  "global_status": "HEALTHY",
  "active_alerts": []
}
```
> **Valores posibles de `global_status`:** `"HEALTHY"` (Verde), `"WARNING"` (Amarillo - deriva moderada o rendimiento degradado), `"CRITICAL"` (Rojo - concept drift crítico o modelo degradado).

---

### 3.2. Salud del Modelo Activo
- **Método / Ruta:** `GET /api/v1/dashboard/health`
- **Query Params:**
  - `operationType` (string, opcional, default: `"VENTA"`)
- **Respuesta (HTTP 200 OK):**
```json
{
  "operation_type": "VENTA",
  "active_model_version": "v1_mlflow",
  "r2": 0.7491,
  "mae": 51533.21,
  "mape": 14.7181,
  "status": "HEALTHY",
  "last_evaluated": "2026-11-08T15:20:00"
}
```

---

### 3.3. Ficha Técnica y Metadatos del Modelo en Servicio
- **Método / Ruta:** `GET /api/v1/dashboard/model-info`
- **Query Params:**
  - `operationType` (string, opcional, default: `"VENTA"`)
- **Descripción:** Proporciona los metadatos completos del modelo cargado en memoria RAM para renderizar la tarjeta técnica de detalles.
- **Respuesta (HTTP 200 OK):**
```json
{
  "version": "v1_mlflow",
  "archivo": "xgboost_venta_v1.pkl",
  "metricas": {
    "mape_pct": 14.7181,
    "r2": 0.7491,
    "mae_soles": 51533.21,
    "rmse_soles": 77832.07,
    "train_periodo": "2016-2023",
    "test_periodo": "2024-2025"
  },
  "ipc_actual": {
    "valor": 169.18,
    "periodo": "2026-Q1",
    "fuente": "INEI/BCRP — serie PN01270PM, Base Dic 2009 = 100",
    "nota": "Restaurado a valor canónico de tesis Q1-2026"
  },
  "n_features": 33,
  "n_distritos": 22,
  "data_provider": "postgresql",
  "distritos_disponibles": [
    "Ate Vitarte", "Barranco", "Bellavista", "Breña", "Carabayllo",
    "Cercado de Lima", "Chorrillos", "Comas", "Jesús María", "La Molina",
    "La Perla", "La Victoria", "Lince", "Los Olivos", "Magdalena",
    "Miraflores", "Pueblo Libre", "San Borja", "San Isidro", "San Miguel",
    "Surco", "Surquillo"
  ]
}
```

---

### 3.4. Detección de Data Drift (Covariables)
- **Método / Ruta:** `GET /api/v1/dashboard/data-drift`
- **Query Params:**
  - `operationType` (string, opcional, default: `"VENTA"`)
  - `range` (string, opcional, default: `"30d"`): `"7d"`, `"30d"`, `"90d"`.
- **Respuesta (HTTP 200 OK):**
```json
{
  "operation_type": "VENTA",
  "temporal_range": "30d",
  "drift_detected": false,
  "share_of_drifted_features": 0.0,
  "drifted_features_count": 0,
  "total_features_count": 33,
  "metrics": [
    {
      "feature_name": "superficie_total",
      "metric_name": "PSI",
      "metric_value": 0.0312,
      "threshold": 0.10,
      "drift_detected": false
    },
    {
      "feature_name": "precio_soles_const",
      "metric_name": "PSI",
      "metric_value": 0.0645,
      "threshold": 0.10,
      "drift_detected": false
    }
  ]
}
```

---

### 3.5. Detección de Concept Drift (Variable Objetivo)
- **Método / Ruta:** `GET /api/v1/dashboard/concept-drift`
- **Query Params:**
  - `operationType` (string, opcional, default: `"VENTA"`)
  - `range` (string, opcional, default: `"30d"`)
- **Respuesta (HTTP 200 OK):**
```json
{
  "operation_type": "VENTA",
  "temporal_range": "30d",
  "drift_detected": false,
  "wasserstein_distance": 0.034,
  "psi_score": 0.048,
  "ks_statistic": 0.021,
  "interpretation": "Distribución del target estable sin deriva significativa"
}
```

---

### 3.6. Fluctuación Temporal de Mercado (Gráfica de Línea Temporal)
- **Método / Ruta:** `GET /api/v1/dashboard/temporal-fluctuation`
- **Query Params:**
  - `operationType` (string, opcional, default: `"VENTA"`)
- **Descripción:** Devuelve la serie temporal trimestral para alimentar la gráfica interactiva de líneas de MAPE y MAE frente al umbral máximo de tolerancia del 15.0%.
- **Respuesta (HTTP 200 OK):**
```json
{
  "operation_type": "venta",
  "threshold_mape": 15.0,
  "time_series": [
    {
      "period": "2024-Q1",
      "mape_pct": 14.18,
      "mae_soles": 48916.21,
      "upper_threshold_mape": 15.0,
      "sample_count": 2590,
      "exceeds_threshold": false
    },
    {
      "period": "2024-Q2",
      "mape_pct": 14.17,
      "mae_soles": 49030.12,
      "upper_threshold_mape": 15.0,
      "sample_count": 3861,
      "exceeds_threshold": false
    },
    {
      "period": "2025-Q3",
      "mape_pct": 15.16,
      "mae_soles": 50978.85,
      "upper_threshold_mape": 15.0,
      "sample_count": 2949,
      "exceeds_threshold": true
    }
  ],
  "average_mape": 14.73,
  "average_mae": 51595.55,
  "total_evaluated_samples": 23737,
  "generated_at": "2026-11-08T15:20:00Z"
}
```

---

### 3.7. Telemetría de Latencia y Verificación SLA
- **Método / Ruta:** `GET /api/v1/dashboard/latency`
- **Query Params:**
  - `operationType` (string, opcional, default: `"VENTA"`)
- **Respuesta (HTTP 200 OK):**
```json
{
  "operation_type": "venta",
  "model_version": "v1_mlflow",
  "p50_latency_ms": 14.2,
  "p95_latency_ms": 28.6,
  "p99_latency_ms": 42.1,
  "sla_threshold_ms": 100.0,
  "meets_sla": true,
  "sample_inferences": 100,
  "artifacts_status": {
    "modelo_pkl": true,
    "metricas_json": true,
    "params_json": true,
    "config_json": true
  },
  "all_artifacts_ready": true,
  "measured_at": "2026-11-08T15:20:00Z"
}
```

---

### 3.8. Telemetría de Hardware en Vivo
- **Método / Ruta:** `GET /api/v1/dashboard/resources`
- **Respuesta (HTTP 200 OK):**
```json
{
  "cpu_usage_pct": 1.5,
  "memory_used_mb": 450.0,
  "memory_total_mb": 1213.0,
  "memory_usage_pct": 37.1,
  "uptime_seconds": 12450.0,
  "active_threads": 8,
  "timestamp": "2026-11-08T15:20:00Z"
}
```

---

## 4. Especificación Detallada: Módulo Gobernanza y Ciclo de Vida

### 4.1. Semáforo de Disponibilidad para Reentrenamiento (Mutex Check)
- **Método / Ruta:** `GET /api/v1/governance/training/availability`
- **Descripción:** Consulta tanto la base de datos relacional local como el contenedor ML remoto para saber si el sistema está libre para iniciar un entrenamiento. Debe llamarse antes de habilitar el botón "Iniciar Reentrenamiento" en el frontend.
- **Respuesta (HTTP 200 OK):**
```json
{
  "disponible_para_entrenar": true,
  "mensaje": "Servicio disponible para iniciar un nuevo entrenamiento"
}
```
*(Si hay un job en curso, `disponible_para_entrenar` es `false` y el frontend debe deshabilitar el botón y mostrar un spinner de estado).*

---

### 4.2. Iniciar Reentrenamiento Asíncrono
- **Método / Ruta:** `POST /api/v1/governance/training`
- **Request Body:**
```json
{
  "operation_type": "VENTA",
  "dataset_version": "v2026_Q1",
  "target_metric": "MAPE",
  "max_depth": 6,
  "learning_rate": 0.04
}
```
> **Validaciones:**
> - `operation_type` (obligatorio): `"VENTA"` o `"ALQUILER"`.
> - `dataset_version`, `target_metric`, `max_depth`, `learning_rate` son opcionales (toman los defaults óptimos si se omiten).

- **Respuestas:**
  - **HTTP 201 Created:** Job aceptado en segundo plano.
  ```json
  {
    "job_id": "8f3b6151-9c33-4a11-8220-71eb24dd2a15",
    "operation_type": "VENTA",
    "status": "RUNNING",
    "dataset_version": "v2026_Q1",
    "target_metric": "MAPE",
    "model_version": null,
    "metrics_r2": null,
    "metrics_mae": null,
    "metrics_mape": null,
    "error_message": null,
    "triggered_by": "admin@valuo.pe",
    "duration_seconds": null,
    "created_at": "2026-11-08T15:21:00",
    "completed_at": null
  }
  ```
  - **HTTP 409 Conflict:** Si ya existe un job en curso (bloqueado por mutex).
  - **HTTP 503 Service Unavailable:** Servicio ML caído.

---

### 4.3. Consultar Estado de un Job (Polling)
- **Método / Ruta:** `GET /api/v1/governance/jobs/{jobId}`
- **Path Variable:** `jobId` (UUID retornado en el POST anterior).
- **Descripción:** El frontend realiza sondeo (*polling*) cada 3-5 segundos hasta que el `status` pase a `"SUCCESS"` o `"FAILED"`.
- **Respuesta (HTTP 200 OK):**
```json
{
  "job_id": "8f3b6151-9c33-4a11-8220-71eb24dd2a15",
  "operation_type": "VENTA",
  "status": "SUCCESS",
  "dataset_version": "v2026_Q1",
  "target_metric": "MAPE",
  "model_version": "xgboost_venta_v3",
  "metrics_r2": 0.7512,
  "metrics_mae": 50210.35,
  "metrics_mape": 14.1250,
  "error_message": null,
  "triggered_by": "admin@valuo.pe",
  "duration_seconds": 32,
  "created_at": "2026-11-08T15:21:00",
  "completed_at": "2026-11-08T15:21:32"
}
```
> **Valores de `status`:** `"PENDING"`, `"RUNNING"`, `"SUCCESS"`, `"FAILED"`.

---

### 4.4. Listado Histórico de Jobs Paginado
- **Método / Ruta:** `GET /api/v1/governance/jobs`
- **Query Params:**
  - `operationType` (string, opcional, default: `"VENTA"`)
  - `page` (int, default: `0`)
  - `size` (int, default: `10`)
- **Respuesta (HTTP 200 OK):**
```json
{
  "content": [
    {
      "job_id": "8f3b6151-9c33-4a11-8220-71eb24dd2a15",
      "operation_type": "VENTA",
      "status": "SUCCESS",
      "dataset_version": "v2026_Q1",
      "target_metric": "MAPE",
      "model_version": "xgboost_venta_v3",
      "metrics_r2": 0.7512,
      "metrics_mae": 50210.35,
      "metrics_mape": 14.1250,
      "triggered_by": "admin@valuo.pe",
      "duration_seconds": 32,
      "created_at": "2026-11-08T15:21:00",
      "completed_at": "2026-11-08T15:21:32"
    }
  ],
  "page": 0,
  "size": 10,
  "total_elements": 1,
  "total_pages": 1,
  "is_last": true
}
```

---

### 4.5. Benchmark Comparativo: Producción vs Candidatos
- **Método / Ruta:** `GET /api/v1/governance/benchmark`
- **Query Params:**
  - `operationType` (string, opcional, default: `"VENTA"`)
  - `solo_ultimo` (boolean, opcional, default: `true`): Si es `true`, muestra el candidato más reciente. Si es `false`, lista todos los candidatos pendientes.
- **Descripción:** Presenta el cuadro comparativo para que el administrador tome la decisión de promover o descartar.
- **Respuesta (HTTP 200 OK):**
```json
{
  "tipo_operacion": "venta",
  "benchmark_referencial_mape": 15.0,
  "modelo_produccion": {
    "nombre_modelo": "xgboost_venta",
    "version": "7",
    "stage": "Production",
    "run_id": "run-prod-123",
    "metricas": {
      "mape_pct": 14.9845,
      "r2": 0.7473,
      "mae": 52121.38,
      "rmse": 78118.43
    },
    "creado_en": "2026-10-06T04:40:00Z",
    "fuente_artefacto": "models:/xgboost_venta/7"
  },
  "candidatos": [
    {
      "nombre_modelo": "xgboost_venta",
      "version": "8",
      "stage": "Candidate",
      "run_id": "run-cand-456",
      "metricas": {
        "mape_pct": 14.1571,
        "r2": 0.7472,
        "mae": 50897.93,
        "rmse": 78128.69
      },
      "deltas": {
        "delta_mape_pct": -0.8274,
        "delta_r2": -0.0001,
        "delta_mae": -1223.45,
        "delta_rmse": 10.26,
        "mejora_mape": true,
        "mejora_r2": false
      },
      "recomendacion": "RECOMENDADO_PARA_PRODUCCION",
      "motivo": "El candidato mejora el MAPE frente a producción (ΔMAPE: -0.83%) y se mantiene por debajo del 15%."
    }
  ],
  "total_candidatos": 1,
  "generado_en": "2026-11-08T15:22:00Z"
}
```

---

### 4.6. Promover Modelo Candidato a Producción
- **Método / Ruta:** `POST /api/v1/governance/promote`
- **Request Body:**
```json
{
  "operation_type": "VENTA",
  "reason": "Aprobado: menor MAPE (14.16%) con validación positiva de benchmark"
}
```
- **Descripción:** Aprueba el candidato, ejecuta Hot-Reload en memoria RAM de `ml-service` (~1.5s sin downtime) y archiva la versión anterior en MLflow.
- **Respuesta (HTTP 200 OK):**
```json
{
  "id": 14,
  "created_at": "2026-11-08T15:23:00",
  "action": "PROMOTION",
  "operation_type": "VENTA",
  "previous_version": "7",
  "new_version": "8",
  "model_version": "8",
  "reason": "Aprobado: menor MAPE (14.16%) con validación positiva de benchmark",
  "performed_by": "admin@valuo.pe",
  "status": "SUCCESS",
  "cambio_metrica": "ΔMAPE: -0.83% | ΔR²: -0.0001 | ΔMAE: -1223.45 (Mejora: Sí)",
  "deltas": {
    "delta_mape_pct": -0.8274,
    "delta_r2": -0.0001,
    "delta_mae": -1223.45,
    "delta_rmse": 10.26,
    "mejora_mape": true,
    "mejora_r2": false
  },
  "previous_stage": "STAGING",
  "new_stage": "PRODUCTION"
}
```

---

### 4.7. Descartar y Archivar Modelo Candidato
- **Método / Ruta:** `POST /api/v1/governance/discard`
- **Request Body:**
```json
{
  "operation_type": "VENTA",
  "version": "8",
  "reason": "Rechazado: MAPE 18.2% supera el umbral máximo de 15.0%"
}
```
> **Nota:** El campo `version` es opcional; si se omite, descarta automáticamente el último candidato activo.
- **Descripción:** Rechaza el modelo candidato, transiciona en MLflow a `Archived`, estampa la etiqueta `estado_gobernanza: "discarded"` y lo retira de la comparativa de `/benchmark`.
- **Respuesta (HTTP 200 OK):**
```json
{
  "id": 15,
  "created_at": "2026-11-08T15:24:00",
  "action": "DISCARD",
  "operation_type": "VENTA",
  "previous_version": null,
  "new_version": "8",
  "model_version": "8",
  "reason": "Rechazado: MAPE 18.2% supera el umbral máximo de 15.0%",
  "performed_by": "admin@valuo.pe",
  "status": "DISCARDED",
  "cambio_metrica": "Candidato descartado y archivado: MAPE=18.20% | R²=0.6912",
  "deltas": null,
  "previous_stage": "CANDIDATE",
  "new_stage": "ARCHIVED"
}
```

---

### 4.8. Rollback Preventivo a Versión Archivada
- **Método / Ruta:** `POST /api/v1/governance/rollback`
- **Request Body:**
```json
{
  "operation_type": "VENTA",
  "target_version": "7",
  "reason": "Reversión preventiva por degradación en el ciclo inmobiliario reciente"
}
```
- **Respuesta (HTTP 200 OK):**
```json
{
  "id": 16,
  "created_at": "2026-11-08T15:25:00",
  "action": "ROLLBACK",
  "operation_type": "VENTA",
  "previous_version": "8",
  "new_version": "7",
  "model_version": "7",
  "reason": "Reversión preventiva por degradación en el ciclo inmobiliario reciente",
  "performed_by": "admin@valuo.pe",
  "status": "SUCCESS",
  "cambio_metrica": "ΔMAPE: +0.83% | ΔR²: +0.0001 | ΔMAE: +1223.45",
  "deltas": {
    "delta_mape_pct": 0.8274,
    "delta_r2": 0.0001,
    "delta_mae": 1223.45,
    "delta_rmse": -10.26,
    "mejora_mape": false,
    "mejora_r2": true
  },
  "previous_stage": "PRODUCTION",
  "new_stage": "PRODUCTION"
}
```

---

### 4.9. Historial de Versiones en MLflow Model Registry
- **Método / Ruta:** `GET /api/v1/governance/models/history`
- **Query Params:**
  - `operationType` (string, opcional, default: `"VENTA"`)
- **Descripción:** Lista todas las versiones registradas en MLflow con sus etapas (`Production`, `Candidate`, `Archived`) para alimentar el combo selector del modal de Rollback.
- **Respuesta (HTTP 200 OK):**
```json
{
  "tipo_operacion": "venta",
  "nombre_modelo": "xgboost_venta",
  "total_versiones": 3,
  "versiones": [
    {
      "nombre_modelo": "xgboost_venta",
      "version": "8",
      "stage": "Production",
      "run_id": "run-prod-8",
      "metricas": {
        "mape_pct": 14.1571,
        "r2": 0.7472,
        "mae": 50897.93,
        "rmse": 78128.69
      },
      "creado_en": "2026-10-07T12:00:00Z"
    },
    {
      "nombre_modelo": "xgboost_venta",
      "version": "7",
      "stage": "Archived",
      "run_id": "run-arch-7",
      "metricas": {
        "mape_pct": 14.9845,
        "r2": 0.7473,
        "mae": 52121.38,
        "rmse": 78118.43
      },
      "creado_en": "2026-10-06T04:40:00Z"
    }
  ]
}
```

---

### 4.10. Consultar Factor Macroeconómico IPC
- **Método / Ruta:** `GET /api/v1/governance/ipc`
- **Respuesta (HTTP 200 OK):**
```json
{
  "ipc_vigente": {
    "valor": 169.18,
    "factor": 1.6918,
    "periodo": "2026-Q1",
    "fuente": "INEI/BCRP — serie PN01270PM, Base Dic 2009 = 100",
    "nota": "Actualizar este valor al cierre de cada trimestre",
    "fecha_actualizacion": "2026-08-01T00:00:00Z"
  },
  "modelo_venta": {
    "valor": 169.18,
    "periodo": "2026-Q1"
  },
  "modelo_alquiler": {
    "valor": 169.18,
    "periodo": "2026-Q1"
  }
}
```

---

### 4.11. Actualizar Factor Macroeconómico IPC en Caliente
- **Método / Ruta:** `PUT /api/v1/governance/ipc`
- **Request Body:**
```json
{
  "valor": 169.50,
  "periodo": "2026-Q2",
  "fuente": "INEI/BCRP — serie PN01270PM, Base Dic 2009 = 100",
  "actualizado_por": "Joaquín Cortez",
  "nota": "Ajuste trimestral por reporte oficial BCRP Q2",
  "aplicar_a": "todos"
}
```
> **Validaciones:**
> - `valor` (obligatorio, número entre 0.0001 y 10.0000).
> - `periodo` (obligatorio, ej. `"2026-Q2"`).
> - `aplicar_a`: `"todos"`, `"venta"` o `"alquiler"`.
- **Respuesta (HTTP 200 OK):**
```json
{
  "exito": true,
  "mensaje": "Factor IPC actualizado exitosamente a 169.5 (2026-Q2) para: venta, alquiler. Inferencia nominal actualizada en caliente.",
  "ipc_nuevo": {
    "valor": 169.5,
    "factor": 1.695,
    "periodo": "2026-Q2"
  },
  "recargado_en_ram": true
}
```

---

## 5. Especificación Detallada: Módulo de Auditoría y Trazabilidad

### 5.1. Consultar Bitácora de Auditoría Pericial Paginada
- **Método / Ruta:** `GET /api/v1/audit/logs`
- **Query Params:**
  - `operationType` (string, opcional): `"VENTA"`, `"ALQUILER"` o `"ALL"`. Si se omite, retorna todos los tipos.
  - `page` (int, default: `0`): Índice de página base 0.
  - `size` (int, default: `20`, máx: `100`): Cantidad de registros por página.
- **Descripción:** Retorna el registro cronológico descendente inmutable de cada acción ejecutada en los modelos.
- **Respuesta (HTTP 200 OK):**
```json
{
  "content": [
    {
      "id": 16,
      "created_at": "2026-11-08T15:25:00",
      "action": "ROLLBACK",
      "operation_type": "VENTA",
      "previous_version": "8",
      "new_version": "7",
      "model_version": "7",
      "reason": "Reversión preventiva por degradación en el ciclo inmobiliario reciente",
      "performed_by": "admin@valuo.pe",
      "status": "SUCCESS",
      "cambio_metrica": "ΔMAPE: +0.83% | ΔR²: +0.0001 | ΔMAE: +1223.45",
      "deltas": {
        "delta_mape_pct": 0.8274,
        "delta_r2": 0.0001,
        "delta_mae": 1223.45,
        "delta_rmse": -10.26,
        "mejora_mape": false,
        "mejora_r2": true
      },
      "previous_stage": "PRODUCTION",
      "new_stage": "PRODUCTION"
    },
    {
      "id": 15,
      "created_at": "2026-11-08T15:24:00",
      "action": "DISCARD",
      "operation_type": "VENTA",
      "previous_version": null,
      "new_version": "8",
      "model_version": "8",
      "reason": "Rechazado: MAPE 18.2% supera el umbral máximo de 15.0%",
      "performed_by": "admin@valuo.pe",
      "status": "DISCARDED",
      "cambio_metrica": "Candidato descartado y archivado: MAPE=18.20% | R²=0.6912",
      "deltas": null,
      "previous_stage": "CANDIDATE",
      "new_stage": "ARCHIVED"
    },
    {
      "id": 14,
      "created_at": "2026-11-08T15:23:00",
      "action": "PROMOTION",
      "operation_type": "VENTA",
      "previous_version": "7",
      "new_version": "8",
      "model_version": "8",
      "reason": "Aprobado: menor MAPE (14.16%) con validación positiva de benchmark",
      "performed_by": "admin@valuo.pe",
      "status": "SUCCESS",
      "cambio_metrica": "ΔMAPE: -0.83% | ΔR²: -0.0001 | ΔMAE: -1223.45 (Mejora: Sí)",
      "deltas": {
        "delta_mape_pct": -0.8274,
        "delta_r2": -0.0001,
        "delta_mae": -1223.45,
        "delta_rmse": 10.26,
        "mejora_mape": true,
        "mejora_r2": false
      },
      "previous_stage": "STAGING",
      "new_stage": "PRODUCTION"
    },
    {
      "id": 13,
      "created_at": "2026-11-08T15:21:32",
      "action": "TRAINING",
      "operation_type": "VENTA",
      "previous_version": "7",
      "new_version": "xgboost_venta_v3",
      "model_version": "xgboost_venta_v3",
      "reason": "Entrenamiento completado y registrado en MLflow",
      "performed_by": "admin@valuo.pe",
      "status": "SUCCESS",
      "cambio_metrica": "Métricas: MAPE=14.13% | R²=0.7512 | MAE=50210.35",
      "deltas": null,
      "previous_stage": "PRODUCTION",
      "new_stage": "CANDIDATE"
    },
    {
      "id": 12,
      "created_at": "2026-11-08T15:21:00",
      "action": "TRAINING",
      "operation_type": "VENTA",
      "previous_version": "7",
      "new_version": "Job: 8f3b6151-9c33-4a11-8220-71eb24dd2a15",
      "model_version": "Job: 8f3b6151-9c33-4a11-8220-71eb24dd2a15",
      "reason": "Reentrenamiento manual iniciado para VENTA",
      "performed_by": "admin@valuo.pe",
      "status": "IN_PROGRESS",
      "cambio_metrica": "Reentrenamiento iniciado en background",
      "deltas": null,
      "previous_stage": "PRODUCTION",
      "new_stage": "CANDIDATE"
    }
  ],
  "page": 0,
  "size": 20,
  "total_elements": 5,
  "total_pages": 1,
  "is_last": true
}
```

---

## 6. Guía Rápida para Estados y Badges en la UI

Para mantener consistencia visual en los componentes React / Vue:

| Campo JSON | Valor | Color Badge UI sugerido | Significado |
|---|---|---|---|
| `status` (en Auditoría) | `"SUCCESS"` | Verde (`bg-green-100 text-green-800`) | Operación exitosa |
| `status` (en Auditoría) | `"IN_PROGRESS"` | Azul (`bg-blue-100 text-blue-800`) | Reentrenamiento corriendo en background |
| `status` (en Auditoría) | `"DISCARDED"` | Gris (`bg-gray-100 text-gray-800`) | Modelo candidato descartado |
| `status` (en Auditoría) | `"FAILED"` | Rojo (`bg-red-100 text-red-800`) | Fallo en entrenamiento o reversión |
| `action` | `"PROMOTION"` | Violeta (`bg-purple-100 text-purple-800`) | Candidato promovido a Producción |
| `action` | `"ROLLBACK"` | Ámbar (`bg-amber-100 text-amber-800`) | Reversión de versión |
| `action` | `"DISCARD"` | Gris (`bg-gray-100 text-gray-800`) | Descarte formal de candidato |
| `action` | `"TRAINING"` | Cian (`bg-cyan-100 text-cyan-800`) | Ciclo de reentrenamiento |
| `global_status` | `"HEALTHY"` | Verde | Modelos con MAPE < 15% y sin deriva |
| `global_status` | `"WARNING"` | Amarillo | Deriva moderada de features detectada |
| `global_status` | `"CRITICAL"` | Rojo | Concept drift o degradación crítica |
| `recomendacion` (Benchmark) | `"RECOMENDADO_PARA_PRODUCCION"` | Verde | Candidato bate a la versión activa |
| `recomendacion` (Benchmark) | `"NO_RECOMENDADO"` | Rojo | Candidato empeora métricas |
| `recomendacion` (Benchmark) | `"REVISION_MANUAL"` | Amarillo | Mejoras ambiguas que requieren criterio pericial |

