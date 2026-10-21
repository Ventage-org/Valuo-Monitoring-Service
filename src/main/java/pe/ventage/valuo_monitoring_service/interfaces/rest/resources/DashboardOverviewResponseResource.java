package pe.ventage.valuo_monitoring_service.interfaces.rest.resources;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record DashboardOverviewResponseResource(
        @JsonProperty("health") ModelHealthResponseResource health,
        @JsonProperty("data_drift") DataDriftResponseResource dataDrift,
        @JsonProperty("concept_drift") ConceptDriftResponseResource conceptDrift,
        @JsonProperty("global_status") String globalStatus,
        @JsonProperty("active_alerts") List<String> activeAlerts
) {}
