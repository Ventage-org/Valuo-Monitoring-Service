package pe.ventage.valuo_monitoring_service.infrastructure.clients.ml;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ml.service")
public record MlClientProperties(
        String url,
        String adminToken,
        int connectTimeoutMs,
        int readTimeoutMs
) {
    public MlClientProperties {
        if (url == null || url.isBlank()) {
            url = "http://localhost:8000";
        }
        if (adminToken == null || adminToken.isBlank()) {
            adminToken = "dev-secret-token";
        }
        if (connectTimeoutMs <= 0) {
            connectTimeoutMs = 5000;
        }
        if (readTimeoutMs <= 0) {
            readTimeoutMs = 15000;
        }
    }
}
