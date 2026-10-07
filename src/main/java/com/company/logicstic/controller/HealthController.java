package com.company.logicstic.controller;

import java.util.Arrays;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private final Environment environment;
    private final ObjectProvider<DataSource> dataSourceProvider;

    public HealthController(Environment environment, ObjectProvider<DataSource> dataSourceProvider) {
        this.environment = environment;
        this.dataSourceProvider = dataSourceProvider;
    }

    @GetMapping({"/actuator/health", "/health", "/api/health"})
    public ResponseEntity<Map<String, String>> health() {
        String[] activeProfiles = environment.getActiveProfiles();
        String profiles = Arrays.stream(
                activeProfiles.length > 0 ? activeProfiles : environment.getDefaultProfiles()
            )
            .sorted()
            .reduce((left, right) -> left + "," + right)
            .orElse("default");

        return ResponseEntity.ok(Map.of(
            "application", environment.getProperty("spring.application.name", "logicstic"),
            "database", dataSourceProvider.getIfAvailable() == null ? "disabled" : "enabled",
            "profiles", profiles,
            "status", "UP"
        ));
    }
}
