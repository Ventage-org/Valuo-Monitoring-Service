# Guía de Integración Backend: Endpoints de Reentrenamiento, Monitoreo y Gobernanza de Modelos ML

**Documento:** Especificación Técnica de APIs para el Módulo de Monitoreo  
**Microservicio:** `ml-service` (FastAPI + XGBoost + MLflow Tracking & Registry)  
**Proyecto:** Valuación Inmobiliaria Masiva en Lima Metropolitana  
**Fecha:** Octubre 2026  
**Versión de API:** `v1`  

---

## 1. Arquitectura de Integración y Principios de Diseño

El sistema separa las responsabilidades entre el **Backend Principal** (NestJS / Node / Spring / Django) y el **Microservicio ML** (`ml-service`):

```
┌─────────────────────────┐          HTTP REST          ┌───────────────────────────┐
│    Backend Principal    │  ─────────────────────────> │        ml-service         │
│  (Módulo de Monitoreo)  │                             │         (FastAPI)         │
│                         │  <───────────────────────── │                           │
└───────────┬─────────────┘      (Polling / Respuestas) └─────────────┬─────────────┘
            │                                                         │
            ▼                                                         ▼
┌─────────────────────────┐                             ┌───────────────────────────┐
│ PostgreSQL Operacional  │                             │ MLflow Tracking & Registry│
│ (db_operacional_valuo)  │                             │  (localhost:5000 / mlops) │
│ - Auditoría de jobs     │                             │ - Versiones de modelos    │
│ - Historial promociones │                             │ - Métricas (MAPE, IAAO)   │
└─────────────────────────┘                             └───────────────────────────┘
```

### Principios Clave:
1. **Asincronía y No-Bloqueo:** Los pipelines de entrenamiento procesan >40,000 registros y calculan SHAP values, tomando entre 25 y 45 segundos. Por ello, el inicio de entrenamiento retorna inmediatamente **HTTP 202 (Accepted)** con un `job_id`. El backend realiza polling sobre `/estado/{job_id}`.
2. **Control de Concurrencia (Semáforo):** Solo se permite **un (1) job de entrenamiento simultáneo**. Si se intenta lanzar un segundo entrenamiento mientras uno está en curso, la API responde **HTTP 409 Conflict**.
3. **Gobernanza Human-in-the-Loop:** Por defecto, los nuevos modelos se registran en MLflow como **`Candidate`** para que el administrador examine sus métricas (MAPE, $R^2$, COD IAAO) en el panel de monitoreo. Si se aprueba, el backend invoca el endpoint de promoción (`/promover`), ejecutando **Hot-Reload en memoria RAM** sin tiempo de inactividad (*zero-downtime*).
4. **Seguridad:** Todas las rutas administrativas (`/api/v1/admin/*`) requieren el header `X-Admin-Token` si la variable de entorno `ADMIN_TOKEN` está configurada en `.env`.

---

## 2. Convenciones Generales de la API

- **Base URL:** `http://localhost:8000` (configurable por variable de entorno `ML_SERVICE_URL`)
- **Headers Estándar:**
  ```http
  Content-Type: application/json
  X-Admin-Token: <ADMIN_TOKEN_CONFIGURADO>
  ```
- **Códigos de Estado HTTP:**
  - `200 OK`: Consulta o promoción exitosa.
  - `202 Accepted`: Job de entrenamiento iniciado exitosamente en background.
  - `401 Unauthorized`: Token de administrador ausente o inválido.
  - `404 Not Found`: `job_id` o modelo no encontrado.
  - `409 Conflict`: Ya existe un job de entrenamiento en ejecución.
  - `422 Unprocessable Entity`: Error de validación de payload JSON.
  - `503 Service Unavailable`: Modelos no inicializados en memoria.

---

## 3. Módulo 1: Reentrenamiento Asíncrono de Modelos

Prefijo: `/api/v1/admin/entrenamiento`

### 3.1. Iniciar Reentrenamiento de Venta
- **Método / Ruta:** `POST /api/v1/admin/entrenamiento/venta`
- **Descripción:** Lanza en segundo plano el pipeline de entrenamiento del modelo de venta sobre `dataset_inmuebles_venta` (PostgreSQL), aplicando target unitario $\ln(P/m^2)$, calibración estratificada IAAO y registro en MLflow.
- **Request Body (Opcional — todos los campos tienen defaults óptimos validados):**
```json
{
  "nombre_modelo": "xgboost_venta_v2",
  "max_depth": 6,
  "learning_rate": 0.040,
  "n_estimators": 650,
  "min_child_weight": 9,
  "usar_target_m2": true,
  "factor_calibracion": null,
  "guardar_como_activo": false,
  "shap_top_n": 10,
  "hiperparametros": {
    "subsample": 0.852,
    "colsample_bytree": 0.847,
    "reg_alpha": 4.06,
    "reg_lambda": 0.0306
  }
}
```
> **Nota sobre `guardar_como_activo`:**
> - `false` (Recomendado): Se registra como **Candidate** en MLflow para ser comparado en el panel de benchmark antes de promoverse.
> - `true`: Pasa directamente a producción y reemplaza al modelo activo en RAM al terminar.

- **Respuesta (HTTP 202 Accepted):**
```json
{
  "job_id": "7dea5252-8f22-4f10-9330-80eb14dd1f16",
  "tipo_operacion": "venta",
  "estado": "en_progreso",
  "nombre_modelo": "xgboost_venta_v2",
  "iniciado_en": "2026-10-06T04:39:41.123456Z",
  "mensaje": "Job de reentrenamiento de venta 'xgboost_venta_v2' iniciado. El pipeline corre en background y registrará el modelo en MLflow.",
  "consultar_estado_en": "/api/v1/admin/entrenamiento/estado/7dea5252-8f22-4f10-9330-80eb14dd1f16"
}
```

---

### 3.2. Iniciar Reentrenamiento de Alquiler
- **Método / Ruta:** `POST /api/v1/admin/entrenamiento/alquiler`
- **Descripción:** Lanza en segundo plano el pipeline de entrenamiento del modelo de alquiler sobre `dataset_inmuebles_alquiler` con ponderación temporal `decay = 0.85`.
- **Request Body (Opcional):**
```json
{
  "nombre_modelo": "xgboost_alquiler_v1",
  "max_depth": 6,
  "learning_rate": 0.035,
  "n_estimators": 600,
  "min_child_weight": 4,
  "usar_target_m2": true,
  "factor_calibracion": null,
  "guardar_como_activo": false,
  "shap_top_n": 10,
  "hiperparametros": {
    "subsample": 0.85,
    "colsample_bytree": 0.80,
    "reg_alpha": 0.1,
    "reg_lambda": 4.0
  }
}
```

- **Respuesta (HTTP 202 Accepted):** Idéntica estructura a Venta con `tipo_operacion: "alquiler"`.

---

### 3.3. Consultar Estado y Métricas de un Job (Polling)
- **Método / Ruta:** `GET /api/v1/admin/entrenamiento/estado/{job_id}`
- **Descripción:** Consulta el estado actual de un job en ejecución o finalizado.
- **Respuesta durante ejecución (`en_progreso`):**
```json
{
  "job_id": "7dea5252-8f22-4f10-9330-80eb14dd1f16",
  "tipo_operacion": "venta",
  "estado": "en_progreso",
  "nombre_modelo": "xgboost_venta_v2",
  "iniciado_en": "2026-10-06T04:39:41.123456Z",
  "completado_en": null,
  "duracion_segundos": null,
  "progreso": "[5/8] Entrenando XGBRegressor (venta, n_estimators=650)...",
  "metricas": null,
  "artefactos": null,
  "mlflow_run_id": null,
  "mlflow_model_version": null,
  "estado_gobernanza": "candidate",
  "error": null
}
```

- **Respuesta al finalizar (`completado`):**
```json
{
  "job_id": "7dea5252-8f22-4f10-9330-80eb14dd1f16",
  "tipo_operacion": "venta",
  "estado": "completado",
  "nombre_modelo": "xgboost_venta_v2",
  "iniciado_en": "2026-10-06T04:39:41.123456Z",
  "completado_en": "2026-10-06T04:40:15.443456Z",
  "duracion_segundos": 34.32,
  "progreso": "[8/8] Entrenamiento de venta completado y registrado como candidato.",
  "metricas": {
    "mape_pct": 14.1620,
    "r2": 0.7472,
    "mae": 51230.15,
    "rmse": 77650.40,
    "cod_pct": 14.12,
    "cov_pct": 18.35,
    "prd": 1.0084,
    "prb": -0.0125,
    "median_ratio_calibrado": 1.0000,
    "n_train": 44173,
    "n_test": 23737,
    "supera_benchmark": true,
    "iaao": {
      "median_ratio": 1.0000,
      "mean_ratio": 1.0142,
      "weighted_mean_ratio": 1.0057,
      "cod_pct": 14.12,
      "cov_pct": 18.35,
      "prd": 1.0084,
      "prb": -0.0125,
      "cumple_iaao_cod": true,
      "cumple_iaao_nivel": true,
      "cumple_iaao_prd": true
    }
  },
  "artefactos": {
    "modelo_pkl": "models/xgboost_venta_v2.pkl",
    "params_json": "models/xgboost_venta_v2_params.json",
    "metricas_json": "models/xgboost_venta_v2_metricas.json",
    "model_config_json": "config/model_config.json",
    "modelo_recargado_en_memoria": false,
    "mlflow_model_uri": "models:/xgboost_venta/8"
  },
  "mlflow_run_id": "0ac890d6d1ca45a8b77b977eb96a0cff",
  "mlflow_model_version": "8",
  "estado_gobernanza": "candidate",
  "error": null
}
```

---

### 3.4. Consultar Último Job y Disponibilidad
- **Método / Ruta:** `GET /api/v1/admin/entrenamiento/estado`
- **Descripción:** Permite saber rápidamente si el servicio está ocupado entrenando o si está disponible para un nuevo job.

### 3.5. Listar Historial de Jobs de la Sesión
- **Método / Ruta:** `GET /api/v1/admin/entrenamiento/jobs`
- **Descripción:** Retorna el array de todos los jobs lanzados en la sesión actual de FastAPI.

---

## 4. Módulo 2: Monitoreo, Gobernanza y Benchmarking

Prefijo: `/api/v1/admin/modelos`

### 4.1. Benchmark Comparativo: Producción vs Candidatos
- **Método / Ruta:** `GET /api/v1/admin/modelos/benchmark/{tipo_operacion}`
- **Parámetros Path:** `tipo_operacion` (`venta` o `alquiler`)
- **Descripción:** Compara matemáticamente el modelo actualmente activo en memoria RAM (`Production`) contra todos los modelos candidatos generados por reentrenamientos en MLflow.
- **Respuesta (HTTP 200 OK):**
```json
{
  "tipo_operacion": "venta",
  "benchmark_referencial_mape": 15.0,
  "modelo_produccion": {
    "nombre_modelo": "xgboost_venta",
    "version": "7",
    "stage": "Production",
    "run_id": "0ac890d6d1ca45a8b77b977eb96a0cff",
    "metricas": {
      "mape_pct": 14.9845,
      "r2": 0.7473,
      "mae": 52121.38,
      "rmse": 78118.43,
      "n_train": 44173,
      "n_test": 23737
    },
    "tags": {
      "autor": "Joaquín Cortez",
      "target": "precio_soles_const"
    },
    "creado_en": "2026-10-06T04:40:00Z",
    "fuente_artefacto": "models:/xgboost_venta/7"
  },
  "candidatos": [
    {
      "candidato": {
        "nombre_modelo": "xgboost_venta",
        "version": "8",
        "stage": "Candidate",
        "run_id": "b1c2d3e4f5a6b7c8d9e0",
        "metricas": {
          "mape_pct": 14.1620,
          "r2": 0.7472,
          "mae": 51230.15,
          "rmse": 77650.40,
          "n_train": 44173,
          "n_test": 23737
        },
        "tags": {
          "tipo_target": "m2",
          "calibracion_iaao": "estratificada"
        },
        "creado_en": "2026-10-07T12:00:00Z",
        "fuente_artefacto": "models:/xgboost_venta/8"
      },
      "deltas": {
        "delta_mape_pct": -0.8225,
        "delta_r2": -0.0001,
        "delta_mae": -891.23,
        "delta_rmse": -468.03,
        "mejora_mape": true,
        "mejora_r2": false
      },
      "recomendacion": "RECOMENDADO_PARA_PRODUCCION",
      "motivo": "El candidato reduce el MAPE en 0.82 puntos porcentuales frente a producción y cumple holgadamente el benchmark de 15.0%."
    }
  ],
  "total_candidatos": 1,
  "generado_en": "2026-10-07T15:50:00Z"
}
```

> **Valores posibles de `recomendacion`:**
> - `RECOMENDADO_PARA_PRODUCCION`: Supera al modelo en producción y bate los benchmarks.
> - `NO_RECOMENDADO`: El error MAPE aumentó respecto a la versión en producción.
> - `REVISION_MANUAL`: Mejoras ambiguas (por ejemplo: menor MAPE pero menor $R^2$).

---

### 4.2. Historial de Versiones en Model Registry
- **Método / Ruta:** `GET /api/v1/admin/modelos/historial/{tipo_operacion}`
- **Descripción:** Lista todas las versiones históricas registradas en MLflow con sus etapas (`Production`, `Candidate`, `Archived`), fecha de creación y métricas.
- **Respuesta (HTTP 200 OK):**
```json
{
  "tipo_operacion": "venta",
  "nombre_modelo": "xgboost_venta",
  "total_versiones": 8,
  "versiones": [
    {
      "nombre_modelo": "xgboost_venta",
      "version": "8",
      "stage": "Candidate",
      "run_id": "b1c2d3e4f5a6b7c8d9e0",
      "metricas": {
        "mape_pct": 14.1620,
        "r2": 0.7472,
        "mae": 51230.15,
        "rmse": 77650.40
      },
      "tags": { "experimento": "target_m2_calibrado" },
      "creado_en": "2026-10-07T12:00:00Z"
    },
    {
      "nombre_modelo": "xgboost_venta",
      "version": "7",
      "stage": "Production",
      "run_id": "0ac890d6d1ca45a8b77b977eb96a0cff",
      "metricas": {
        "mape_pct": 14.9845,
        "r2": 0.7473,
        "mae": 52121.38,
        "rmse": 78118.43
      },
      "tags": {},
      "creado_en": "2026-10-06T04:40:00Z"
    }
  ],
  "generado_en": "2026-10-07T15:50:00Z"
}
```

---

### 4.3. Promover Modelo Candidato a Producción (Aprobación Humana)
- **Método / Ruta:** `POST /api/v1/admin/modelos/promover`
- **Descripción:** Acción definitiva de gobernanza de MLOps:
  1. Marca en MLflow la versión elegida como `Production` y archiva (`Archived`) la versión previa.
  2. Descarga y carga el artefacto binario directamente en la **memoria RAM** de `ml-service`.
  3. Reconstruye en caliente el `TreeExplainer` de SHAP en RAM sin reiniciar el contenedor/servidor.
  4. Sincroniza `model_config.json` con las nuevas métricas activas.
- **Request Body:**
```json
{
  "tipo_operacion": "venta",
  "version": "8",
  "motivo": "Aprobado por el comité de tasación tras auditoría IAAO: MAPE bajó a 14.16% y COD a 14.12%."
}
```

- **Respuesta (HTTP 200 OK):**
```json
{
  "tipo_operacion": "venta",
  "nombre_modelo": "xgboost_venta",
  "version_promovida": "8",
  "version_anterior_archivada": "7",
  "nuevo_stage": "Production",
  "modelo_recargado_en_memoria": true,
  "mape_nuevo": 14.1620,
  "r2_nuevo": 0.7472,
  "mensaje": "Modelo 'xgboost_venta' versión 8 promovido exitosamente a Production y recargado en memoria RAM.",
  "timestamp": "2026-10-07T15:52:00Z"
}
```

---

## 5. Módulo 3: Observabilidad y Metadatos Activos

### 5.1. Health Check Operacional
- **Método / Ruta:** `GET /health`
- **Descripción:** Diseñado para *health check probes* de Kubernetes, Nginx o comprobación periódica del backend.
- **Respuesta (HTTP 200 OK):**
```json
{
  "status": "ok",
  "modelo_venta": "v8_mlflow",
  "modelo_alquiler": "v4_mlflow",
  "ipc_periodo": "2026-Q1"
}
```
*(Si alguno de los modelos no está cargado en RAM, `status` pasa a `"degraded"`)*.

---

### 5.2. Información y Metadatos del Modelo en Servicio
- **Método / Ruta:** `GET /api/v1/modelo/info?tipo_operacion=venta`
- **Query Params:** `tipo_operacion` (`venta` o `alquiler`, default `venta`)
- **Descripción:** Retorna el estado completo del modelo que atiende las predicciones en este momento:
```json
{
  "version": "v8_mlflow",
  "archivo": "xgboost_venta_v2.pkl",
  "metricas": {
    "mape_pct": 14.1620,
    "r2": 0.7472,
    "mae_soles": 51230.15,
    "rmse_soles": 77650.40,
    "train_periodo": "2016-2023",
    "test_periodo": "2024-2025"
  },
  "ipc_actual": {
    "valor": 169.18,
    "periodo": "2026-Q1",
    "fuente": "INEI/BCRP — serie PN01270PM, Base Dic 2009 = 100",
    "nota": "Actualizar este valor al cierre de cada trimestre sin necesidad de redespliegue"
  },
  "n_features": 33,
  "n_distritos": 22,
  "data_provider": "postgresql",
  "distritos_disponibles": [
    "Ate Vitarte",
    "Barranco",
    "Bellavista",
    "Breña",
    "Callao",
    "Carabayllo",
    "Chorrillos",
    "Comas",
    "Jesus Maria",
    "La Molina",
    "La Victoria",
    "Lima",
    "Lince",
    "Los Olivos",
    "Magdalena del Mar",
    "Miraflores",
    "Pueblo Libre",
    "San Borja",
    "San Isidro",
    "San Miguel",
    "Santiago de Surco",
    "Surquillo"
  ]
}
```

---

## 6. Diccionario de Métricas Oficiales: ML Clásico vs Normas IAAO

Para construir la interfaz y la lógica de validación en el Backend, este es el significado exacto de las métricas generadas:

| Categoría | Métrica en JSON | Rango / Umbral Objetivo | Interpretación Técnica en Tasación |
| :--- | :--- | :---: | :--- |
| **Error Relativo** | `mape_pct` | $\le 15.0\%$ (Venta)<br>$\le 17.89\%$ (Alquiler) | Porcentaje promedio de desviación absoluta sobre el valor real. Venta actual: **14.16%**, Alquiler actual: **13.76%**. |
| **Ajuste Global** | `r2` | $\ge 0.70$ (Venta)<br>$\ge 0.65$ (Alquiler) | Varianza de los precios de mercado explicada por las 33 features socioeconómicas y físicas. |
| **Error Escalar** | `mae` / `rmse` | Menor es mejor | Error en Soles constantes (sin inflación). MAE penaliza linealmente; RMSE penaliza cuadráticamente grandes desvíos. |
| **Nivel IAAO** | `median_ratio` | **`0.90 – 1.10`** (Ideal: 1.00) | Mediana del cociente $\frac{\text{Predicho}}{\text{Real}}$. Con calibración estratificada, se ubica exactamente en **1.0000**. |
| **Dispersión IAAO** | `cod_pct` | **`5.0% – 15.0%`** | Coeficiente de Dispersión (COD). Mide la equidad horizontal (dispersión de errores respecto a la mediana). Actual: **14.12%** ✅. |
| **Variación IAAO**| `cov_pct` | $\le 20.0\%$ | Coeficiente de variación paramétrico basado en desviación estándar de ratios. |
| **Equidad Vertical**| `prd` | **`0.98 – 1.03`** (Ideal: 1.00) | Diferencial Relacionado con el Precio (*Price-Related Differential*). Si es $>1.03$ hay regresividad; si es $<0.98$ progresividad. Actual: **1.0084** ✅. |
| **Sesgo de Precio** | `prb` | **`-0.05 a +0.05`** | Sesgo Relacionado con el Precio (*Price-Related Bias*). Regresión del ratio sobre el logaritmo del precio. Actual: **-0.0125** ✅. |

---

## 7. Recomendaciones para el Backend Principal (NestJS / Node / Spring)

### 7.1. Esquema de Base de Datos para Monitoreo (PostgreSQL Operacional)
Se sugiere crear en `db_operacional_valuo` (o en la base de datos de tu backend) una tabla de auditoría para almacenar las corridas de entrenamiento y las promociones:

```sql
CREATE TABLE schema_monitoreo.historial_entrenamientos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id VARCHAR(64) NOT NULL UNIQUE,
    tipo_operacion VARCHAR(20) NOT NULL, -- 'venta' | 'alquiler'
    estado VARCHAR(20) NOT NULL,         -- 'en_progreso', 'completado', 'fallido'
    nombre_modelo VARCHAR(100) NOT NULL,
    mlflow_run_id VARCHAR(64),
    mlflow_version VARCHAR(20),
    estado_gobernanza VARCHAR(20),       -- 'candidate', 'production'
    mape_pct NUMERIC(6,4),
    r2 NUMERIC(6,4),
    mae NUMERIC(12,2),
    rmse NUMERIC(12,2),
    cod_iaao_pct NUMERIC(6,4),
    prd_iaao NUMERIC(6,4),
    prb_iaao NUMERIC(6,4),
    median_ratio NUMERIC(6,4),
    supera_benchmark BOOLEAN,
    iniciado_en TIMESTAMPTZ NOT NULL,
    completado_en TIMESTAMPTZ,
    duracion_segundos NUMERIC(8,2),
    iniciado_por_usuario_id UUID,
    error_detalle TEXT
);

CREATE TABLE schema_monitoreo.auditoria_promociones (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tipo_operacion VARCHAR(20) NOT NULL,
    version_promovida VARCHAR(20) NOT NULL,
    version_archivada VARCHAR(20),
    motivo TEXT NOT NULL,
    promovido_por_usuario_id UUID NOT NULL,
    fecha_promocion TIMESTAMPTZ DEFAULT clock_timestamp()
);
```

### 7.2. Patrón de Servicio de Monitoreo (Ejemplo en TypeScript / NestJS)

```typescript
// ml-monitoring.service.ts
import { Injectable, HttpException, HttpStatus } from '@nestjs/common';
import axios, { AxiosInstance } from 'axios';

@Injectable()
export class MlMonitoringService {
  private client: AxiosInstance;

  constructor() {
    this.client = axios.create({
      baseURL: process.env.ML_SERVICE_URL || 'http://localhost:8000',
      headers: {
        'X-Admin-Token': process.env.ML_ADMIN_TOKEN || '',
      },
      timeout: 10000, // 10s para consultas rápidas
    });
  }

  // 1. Disparar reentrenamiento
  async triggerTraining(tipo: 'venta' | 'alquiler', options?: any) {
    const response = await this.client.post(`/api/v1/admin/entrenamiento/${tipo}`, options || {});
    return response.data; // { job_id, estado, ... }
  }

  // 2. Polling de estado
  async getJobStatus(jobId: string) {
    const response = await this.client.get(`/api/v1/admin/entrenamiento/estado/${jobId}`);
    return response.data;
  }

  // 3. Obtener Benchmark Producción vs Candidatos
  async getBenchmark(tipo: 'venta' | 'alquiler') {
    const response = await this.client.get(`/api/v1/admin/modelos/benchmark/${tipo}`);
    return response.data;
  }

  // 4. Promover modelo candidato a Producción
  async promoteModel(tipo: 'venta' | 'alquiler', version: string, motivo: string) {
    const response = await this.client.post('/api/v1/admin/modelos/promover', {
      tipo_operacion: tipo,
      version,
      motivo,
    });
    return response.data;
  }

  // 5. Estado de salud y metadata del modelo en RAM
  async getActiveModelInfo(tipo: 'venta' | 'alquiler' = 'venta') {
    const response = await this.client.get(`/api/v1/modelo/info?tipo_operacion=${tipo}`);
    return response.data;
  }
}
```

### 7.3. Estrategia de Polling Recomendada para el Backend
1. Al recibir `job_id`, guardar en PostgreSQL el registro con `estado = 'en_progreso'`.
2. Lanzar un worker o tarea en segundo plano con intervalos de **3 a 5 segundos** para consultar `GET /estado/{job_id}`.
3. Timeout de seguridad: **120 segundos** (el job habitualmente dura ~35s).
4. Al detectar `estado === 'completado'`, actualizar la fila en PostgreSQL con todas las métricas de ML y las métricas `iaao`. Emitir una notificación WebSocket al panel de administración o dashboard de monitoreo.
