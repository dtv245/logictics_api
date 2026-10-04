package com.company.logicstic.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.security.Principal;
import java.util.Optional;

@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "utcAuditTime")
@Profile("!nodb")
public class JpaAuditingConfiguration {

    @Bean
    org.springframework.data.auditing.DateTimeProvider utcAuditTime() {
        return () -> Optional.of(java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC));
    }

    @Bean
    AuditorAware<String> auditorAware() {
        return () -> Optional.ofNullable(RequestContextHolder.getRequestAttributes())
                .filter(ServletRequestAttributes.class::isInstance)
                .map(ServletRequestAttributes.class::cast)
                .map(ServletRequestAttributes::getRequest)
                .map(HttpServletRequest::getUserPrincipal)
                .map(Principal::getName);
    }
}
