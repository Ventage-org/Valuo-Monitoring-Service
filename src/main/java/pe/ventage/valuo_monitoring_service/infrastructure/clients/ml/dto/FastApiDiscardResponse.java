package pe.ventage.valuo_monitoring_service.infrastructure.clients.ml.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public record FastApiDiscardResponse(
        @JsonProperty("exito") Boolean exito,
        @JsonProperty("mensaje") String mensaje,
        @JsonProperty("tipo_operacion") String tipoOperacion,
        @JsonProperty("version_descartada") String versionDescartada,
        @JsonProperty("nuevo_stage") String nuevoStage,
        @JsonProperty("motivo") String motivo,
        @JsonProperty("metricas") Map<String, Object> metricas,
        @JsonProperty("descartado_en") String descartadoEn
) {}

