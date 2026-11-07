package pe.ventage.valuo_monitoring_service.interfaces.rest.resources;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record DiscardModelResource(
        @NotBlank(message = "operation_type es obligatorio")
        @Pattern(regexp = "^(VENTA|ALQUILER)$", message = "operation_type debe ser VENTA o ALQUILER")
        @JsonProperty("operation_type")
        String operationType,

        @JsonProperty("version")
        String version,

        @NotBlank(message = "reason es obligatorio")
        @JsonProperty("reason")
        String reason
) {}

