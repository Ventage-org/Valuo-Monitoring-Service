package pe.ventage.valuo_monitoring_service.interfaces.rest.resources;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RollbackModelResource(
        @NotBlank(message = "operation_type es obligatorio")
        @Pattern(regexp = "^(VENTA|ALQUILER)$", message = "operation_type debe ser VENTA o ALQUILER")
        @JsonProperty("operation_type")
        String operationType,

        @NotBlank(message = "target_version es obligatorio")
        @JsonProperty("target_version")
        String targetVersion,

        @NotBlank(message = "reason es obligatorio")
        @JsonProperty("reason")
        String reason
) {}
