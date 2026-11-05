package com.company.logicstic.ai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfiguration {
    @Bean
    public ObjectMapper aiDispatchObjectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    @Bean
    public RestClient.Builder aiDispatchRestClientBuilder() {
        return RestClient.builder();
    }
}
