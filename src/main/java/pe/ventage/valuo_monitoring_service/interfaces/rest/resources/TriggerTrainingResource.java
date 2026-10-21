package pe.ventage.valuo_monitoring_service.interfaces.rest.resources;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record TriggerTrainingResource(
        @NotBlank(message = "operation_type es obligatorio")
        @Pattern(regexp = "^(VENTA|ALQUILER)$", message = "operation_type debe ser VENTA o ALQUILER")
        @JsonProperty("operation_type")
        String operationType,

        @JsonProperty("dataset_version")
        String datasetVersion,

        @JsonProperty("target_metric")
        String targetMetric,

        @Min(value = 3, message = "max_depth debe ser al menos 3")
        @Max(value = 15, message = "max_depth no puede ser mayor a 15")
        @JsonProperty("max_depth")
        Integer maxDepth,

        @DecimalMin(value = "0.001", message = "learning_rate debe ser al menos 0.001")
        @DecimalMax(value = "1.0", message = "learning_rate no puede ser mayor a 1.0")
        @JsonProperty("learning_rate")
        Double learningRate
) {}
