package com.company.logicstic.config;

import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DateTimeConfig {

    @Bean
    public ZoneId appZoneId(@Value("${app.timezone:UTC}") String timezone) {
        return ZoneId.of(timezone);
    }
}
