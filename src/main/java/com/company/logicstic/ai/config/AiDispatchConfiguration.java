package com.company.logicstic.ai.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiDispatchProperties.class)
public class AiDispatchConfiguration {
}
