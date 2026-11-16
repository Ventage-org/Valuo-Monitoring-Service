package pe.ventage.valuo_monitoring_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;

import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableFeignClients(basePackages = "pe.ventage.valuo_monitoring_service.infrastructure.clients.ml")
@ConfigurationPropertiesScan
@EnableScheduling
public class ValuoMonitoringServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ValuoMonitoringServiceApplication.class, args);
    }
}
