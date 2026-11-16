package pe.ventage.valuo_monitoring_service.infrastructure.clients.ml;

import feign.RequestInterceptor;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Configuration
@Slf4j
public class MlFeignConfig {

    private final MlClientProperties properties;

    public MlFeignConfig(MlClientProperties properties) {
        this.properties = properties;
    }

    @Bean
    public RequestInterceptor adminTokenRequestInterceptor() {
        return template -> {
            if (properties.adminToken() != null && !properties.adminToken().isBlank()) {
                template.header("X-Admin-Token", properties.adminToken());
            }
        };
    }

    @Bean
    public feign.Request.Options requestOptions() {
        return new feign.Request.Options(10, java.util.concurrent.TimeUnit.SECONDS, 60, java.util.concurrent.TimeUnit.SECONDS, true);
    }

    @Bean
    public ErrorDecoder errorDecoder() {
        return (methodKey, response) -> {
            String body = readBody(response);
            log.error("ML Service error response: method={}, status={}, body={}", methodKey, response.status(), body);
            return new MlServiceException(response.status(), "ML Service returned HTTP " + response.status() + ": " + body);
        };
    }

    private String readBody(Response response) {
        if (response.body() == null) return "No response body";
        try (InputStream is = response.body().asInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "Unable to read error body: " + e.getMessage();
        }
    }
}
