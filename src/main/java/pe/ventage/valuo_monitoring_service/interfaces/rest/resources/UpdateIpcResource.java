package pe.ventage.valuo_monitoring_service.interfaces.rest.resources;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record UpdateIpcResource(
        @NotNull(message = "El valor del IPC es obligatorio")
        @Positive(message = "El valor del IPC debe ser positivo")
        @JsonProperty("valor")
        Double valor,

        @JsonProperty("periodo")
        String periodo,

        @JsonProperty("fuente")
        String fuente,

        @JsonProperty("actualizado_por")
        String actualizadoPor,

        @JsonProperty("nota")
        String nota,

        @JsonProperty("aplicar_a")
        String aplicarA
) {}
