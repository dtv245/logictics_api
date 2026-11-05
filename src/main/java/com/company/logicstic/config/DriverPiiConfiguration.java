package com.company.logicstic.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DriverPiiProperties.class)
public class DriverPiiConfiguration {
}
