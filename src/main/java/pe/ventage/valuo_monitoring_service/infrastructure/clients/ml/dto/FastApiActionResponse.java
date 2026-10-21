package pe.ventage.valuo_monitoring_service.infrastructure.clients.ml.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public record FastApiActionResponse(
        @JsonProperty("exito") Boolean exito,
        @JsonProperty("mensaje") String mensaje,
        @JsonProperty("tipo_operacion") String tipoOperacion,
        @JsonProperty("nombre_modelo") String nombreModelo,
        @JsonProperty("version_promovida") String versionPromovida,
        @JsonProperty("version_anterior_archivada") String versionAnteriorArchivada,
        @JsonProperty("nuevo_stage") String nuevoStage,
        @JsonProperty("modelo_cargado_en_ram") Boolean modeloCargadoEnRam,
        @JsonProperty("modelo_recargado_en_memoria") Boolean modeloRecargadoEnMemoria,
        @JsonProperty("metricas") Map<String, Object> metricas,
        @JsonProperty("metricas_anteriores") Map<String, Object> metricasAnteriores,
        @JsonProperty("deltas") Map<String, Object> deltas,
        @JsonProperty("promovido_en") String promovidoEn,
        @JsonProperty("timestamp") String timestamp
) {}
