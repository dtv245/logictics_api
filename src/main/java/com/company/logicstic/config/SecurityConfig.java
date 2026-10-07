package com.company.logicstic.config;

import com.company.logicstic.integration.lark.auth.LarkAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.Customizer;
import org.springframework.http.HttpMethod;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final LarkAuthenticationFilter larkAuthenticationFilter;
    private final org.springframework.beans.factory.ObjectProvider<TenantJwtClaimFilter> tenantFilters;

    public SecurityConfig(LarkAuthenticationFilter larkAuthenticationFilter,
            org.springframework.beans.factory.ObjectProvider<TenantJwtClaimFilter> tenantFilters) {
        this.larkAuthenticationFilter = larkAuthenticationFilter;
        this.tenantFilters = tenantFilters;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .addFilterBefore(larkAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) ->
                                com.company.logicstic.config.SecurityResponses.write(request, response, 401, "UNAUTHORIZED", "Authentication is required"))
                        .accessDeniedHandler((request, response, exception) ->
                                com.company.logicstic.config.SecurityResponses.write(request, response, 403, "FORBIDDEN", "Access is forbidden")))
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/health", "/api/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/lark/authorize", "/api/auth/lark/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/lark/callback", "/api/payroll/provider-callbacks/{provider}").permitAll()

                        // General authenticated user endpoints
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/me").authenticated()
                        .requestMatchers("/api/driver/me/payslips", "/api/payslips/**").authenticated()
                        .requestMatchers("/api/messages/**").authenticated()
                        .requestMatchers("/api/notifications/**").authenticated()

                        // Admin-only management
                        .requestMatchers("/api/roles/**").hasRole("ADMIN")
                        .requestMatchers("/api/internal/**").hasRole("ADMIN")
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/fleet/policies/**").hasRole("ADMIN")
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/optimization/policies/**").hasRole("ADMIN")
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/optimization/qualified-inputs").hasRole("ADMIN")

                        // Fleet management
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/fleet/status-events", "/api/fleet/mileage-attributions")
                        .hasAnyRole("ADMIN", "DISPATCHER")
                        .requestMatchers("/api/fleet/**")
                        .hasAnyRole("ADMIN", "ACCOUNTANT", "DISPATCHER")

                        // Optimization
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/optimization/qualified-inputs/forecasts")
                        .hasAnyRole("ADMIN", "ACCOUNTANT")
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/optimization/qualified-inputs/**")
                        .hasAnyRole("ADMIN", "ACCOUNTANT", "DISPATCHER")
                        .requestMatchers("/api/optimization/**")
                        .hasAnyRole("ADMIN", "DISPATCHER")

                        // Billing & Invoices
                        .requestMatchers("/api/invoices/**")
                        .hasAnyRole("ADMIN", "ACCOUNTANT")
                        .requestMatchers("/api/payments/**")
                        .hasAnyRole("ADMIN", "ACCOUNTANT")

                        // Rating & Rates
                        .requestMatchers("/api/rating/contracts/**", "/api/rating/rules/**", "/api/rating/snapshots/**", "/api/rating/**")
                        .hasAnyRole("ADMIN", "ACCOUNTANT")
                        .requestMatchers("/api/loads/*/requested-pickup-business-date")
                        .hasAnyRole("ADMIN", "ACCOUNTANT", "DISPATCHER")
                        .requestMatchers("/api/loads/*/rating/**")
                        .hasAnyRole("ADMIN", "ACCOUNTANT")

                        // Expenses & Accessorials
                        .requestMatchers("/api/expenses/*/approve")
                        .hasAnyRole("ADMIN", "ACCOUNTANT")
                        .requestMatchers("/api/accessorial-charges/*/approve")
                        .hasAnyRole("ADMIN", "ACCOUNTANT")
                        .requestMatchers("/api/loads/*/accessorials", "/api/trip-stops/*/calculate-detention", "/api/accessorial-charges/**")
                        .hasAnyRole("ADMIN", "ACCOUNTANT", "DISPATCHER")

                        // Reports
                        .requestMatchers("/api/reports/revenue", "/api/reports/financials/**",
                                "/api/customers/*/balance", "/api/reports/expenses",
                                "/api/reports/fleet/**", "/api/reports/costs/**",
                                "/api/loads/*/financial-summary", "/api/reports/profitability/**",
                                "/api/reports/**")
                        .hasAnyRole("ADMIN", "ACCOUNTANT", "PAYROLL", "PAYROLL_MANAGER")

                        // Payroll & Settlements
                        .requestMatchers("/api/driver-pay-policies/**", "/api/driver-settlements/**", "/api/pay-periods/**", "/api/payroll/**")
                        .hasAnyRole("ADMIN", "ACCOUNTANT", "PAYROLL", "PAYROLL_MANAGER")
                        .requestMatchers("/api/loads/*/costs/**")
                        .hasAnyRole("ADMIN", "ACCOUNTANT", "PAYROLL", "PAYROLL_MANAGER")

                        // Core Operations Entities
                        .requestMatchers("/api/employees/**").hasAnyRole("ADMIN", "DISPATCHER")
                        .requestMatchers("/api/drivers/**").hasAnyRole("ADMIN", "DISPATCHER")
                        .requestMatchers("/api/customers/**").hasAnyRole("ADMIN", "ACCOUNTANT", "DISPATCHER")
                        .requestMatchers("/api/trucks/**").hasAnyRole("ADMIN", "ACCOUNTANT", "DISPATCHER")
                        .requestMatchers("/api/loads/**").hasAnyRole("ADMIN", "ACCOUNTANT", "DISPATCHER")
                        .requestMatchers("/api/trips/**", "/api/trip-stops/**").hasAnyRole("ADMIN", "DISPATCHER")
                        .requestMatchers("/api/documents/**").hasAnyRole("ADMIN", "ACCOUNTANT", "DISPATCHER")
                        .requestMatchers("/api/inspections/**").hasAnyRole("ADMIN", "DISPATCHER")

                        // Fallback: All other requests require authentication
                        .anyRequest().authenticated()
                );
        tenantFilters.ifAvailable(filter -> http.addFilterAfter(filter, LarkAuthenticationFilter.class));
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(@Value("${app.security.allowed-origins:}") String origins) {
        var configuration = new CorsConfiguration();
        var allowed = java.util.Arrays.stream(origins.split(",")).map(String::trim).filter(value -> !value.isEmpty()).toList();
        if (allowed.stream().anyMatch(value -> value.contains("*")))
            throw new IllegalArgumentException("CORS origins must be an explicit allowlist");
        configuration.setAllowedOrigins(allowed);
        configuration.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(java.util.List.of("Authorization", "Content-Type", "X-Request-Id"));
        configuration.setAllowCredentials(false);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
