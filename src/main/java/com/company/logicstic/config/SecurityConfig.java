package com.company.logicstic.config;

import com.company.logicstic.integration.lark.auth.LarkAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final LarkAuthenticationFilter larkAuthenticationFilter;

    public SecurityConfig(LarkAuthenticationFilter larkAuthenticationFilter) {
        this.larkAuthenticationFilter = larkAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .addFilterBefore(larkAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/lark/**").permitAll()
                        .requestMatchers("/api/payroll/provider-callbacks/**").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/fleet/policies/**")
                        .hasRole("ADMIN")
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/fleet/status-events", "/api/fleet/mileage-attributions")
                        .hasAnyRole("ADMIN", "DISPATCHER")
                        .requestMatchers("/api/fleet/**")
                        .hasAnyRole("ADMIN", "ACCOUNTANT", "DISPATCHER")
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/optimization/policies/**")
                        .hasRole("ADMIN")
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/optimization/qualified-inputs/forecasts")
                        .hasAnyRole("ADMIN", "ACCOUNTANT")
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/optimization/qualified-inputs/**")
                        .hasRole("ADMIN")
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/optimization/qualified-inputs/**")
                        .hasAnyRole("ADMIN", "ACCOUNTANT", "DISPATCHER")
                        .requestMatchers("/api/optimization/**")
                        .hasAnyRole("ADMIN", "DISPATCHER")
                        .requestMatchers("/api/invoices/**")
                        .hasAnyRole("ADMIN", "ACCOUNTANT")
                        .requestMatchers("/api/rating/contracts/**", "/api/rating/rules/**", "/api/rating/snapshots/**")
                        .hasAnyRole("ADMIN", "ACCOUNTANT")
                        .requestMatchers("/api/loads/*/requested-pickup-business-date")
                        .hasAnyRole("ADMIN", "ACCOUNTANT", "DISPATCHER")
                        .requestMatchers("/api/loads/*/rating/**")
                        .hasAnyRole("ADMIN", "ACCOUNTANT")
                        .requestMatchers("/api/driver/me/payslips", "/api/payslips/**").authenticated()
                        .requestMatchers("/api/expenses/*/approve")
                        .hasAnyRole("ADMIN", "ACCOUNTANT")
                        .requestMatchers("/api/accessorial-charges/*/approve")
                        .hasAnyRole("ADMIN", "ACCOUNTANT")
                        .requestMatchers("/api/loads/*/accessorials", "/api/trip-stops/*/calculate-detention")
                        .hasAnyRole("ADMIN", "ACCOUNTANT", "DISPATCHER")
                        .requestMatchers("/api/reports/revenue", "/api/reports/financials/**",
                                "/api/customers/*/balance", "/api/reports/expenses",
                                "/api/reports/fleet/**", "/api/reports/costs/**",
                                "/api/loads/*/financial-summary", "/api/reports/profitability/**")
                        .hasAnyRole("ADMIN", "ACCOUNTANT", "PAYROLL", "PAYROLL_MANAGER")
                        .requestMatchers("/api/driver-pay-policies/**", "/api/driver-settlements/**", "/api/pay-periods/**", "/api/payroll/**")
                        .hasAnyRole("ADMIN", "ACCOUNTANT", "PAYROLL", "PAYROLL_MANAGER")
                        .requestMatchers("/api/loads/*/costs/**")
                        .hasAnyRole("ADMIN", "ACCOUNTANT", "PAYROLL", "PAYROLL_MANAGER")
                        .anyRequest().permitAll()
                );
        return http.build();
    }
}
