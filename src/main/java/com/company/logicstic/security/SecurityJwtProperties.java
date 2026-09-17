package com.company.logicstic.security;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.security.jwt")
public record SecurityJwtProperties(
    @NotBlank String issuer, @NotBlank String audience, @NotBlank String jwkSetUri) {}
