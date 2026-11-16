package pe.ventage.valuo_monitoring_service.infrastructure.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI valuoMonitoringOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Valuo - Monitoring & Governance Service API")
                        .description("Microservicio #6 del ecosistema Valuo. Gestiona la observabilidad de modelos ML (salud, data drift, concept drift) y la gobernanza del ciclo de vida (reentrenamiento, promocion, rollback y auditoria).")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Equipo Valuo")
                                .email("soporte@valuo.pe"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://valuo.pe")))
                .servers(List.of(
                        new Server().url("http://localhost:8082").description("Servidor Local de Desarrollo")
                ));
    }
}
