package pe.ventage.valuo_monitoring_service.interfaces.rest.resources;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record PageResponseResource<T>(
        @JsonProperty("content") List<T> content,
        @JsonProperty("page") int page,
        @JsonProperty("size") int size,
        @JsonProperty("total_elements") long totalElements,
        @JsonProperty("total_pages") int totalPages,
        @JsonProperty("is_last") boolean isLast
) {}
