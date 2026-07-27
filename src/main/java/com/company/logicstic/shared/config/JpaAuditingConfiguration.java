package com.company.logicstic.shared.config;

import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Populates the four audit columns on {@link com.company.logicstic.shared.BaseAuditableEntity}.
 *
 * <p>{@code dateTimeProviderRef} is not optional here. Spring Data's default provider hands back a
 * {@code LocalDateTime}, and its converter cannot turn that into the {@code OffsetDateTime} the
 * entity declares — every insert of an auditable entity failed with "Cannot convert unsupported
 * date type java.time.LocalDateTime to java.time.OffsetDateTime". Unit tests never caught it
 * because they mock the repositories; it only appears against a real database.
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
@Profile("!nodb")
public class JpaAuditingConfiguration {

  /**
   * UTC, to match {@code hibernate.jdbc.time_zone: UTC} in {@code application.yml} and the {@code
   * timestamptz} columns. Stamping audit rows in the server's local zone would make the history of
   * a multi-region deployment depend on where the process happened to run.
   */
  @Bean
  DateTimeProvider auditingDateTimeProvider() {
    return () -> Optional.of(OffsetDateTime.now(ZoneOffset.UTC));
  }

  @Bean
  AuditorAware<String> auditorAware() {
    return () ->
        Optional.ofNullable(RequestContextHolder.getRequestAttributes())
            .filter(ServletRequestAttributes.class::isInstance)
            .map(ServletRequestAttributes.class::cast)
            .map(ServletRequestAttributes::getRequest)
            .map(HttpServletRequest::getUserPrincipal)
            .map(Principal::getName);
  }
}
