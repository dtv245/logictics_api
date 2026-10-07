package com.company.logicstic;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import com.company.logicstic.config.NoDbComponentFilter;

@SpringBootApplication
@ComponentScan(excludeFilters = {
    @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
    @ComponentScan.Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class),
    @ComponentScan.Filter(type = FilterType.CUSTOM, classes = NoDbComponentFilter.class)
})
public class LogisticApplication {

    public static void main(String[] args) {
        SpringApplication.run(LogisticApplication.class, args);
    }
}
