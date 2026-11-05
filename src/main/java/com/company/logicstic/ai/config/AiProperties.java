package com.company.logicstic.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.Map;

@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
    boolean gapEnabled,
    boolean dispatchEnabled,
    boolean dryRun,
    int debounceSeconds,
    int cooldownMinutes,
    int maxAsks,
    String defaultProvider,
    String activeModel,
    String apiKey,
    String baseUrl,
    int maxTokens,
    int timeoutSeconds,
    Map<String, TenantAiConfig> tenants
) {
    public record TenantAiConfig(
        Boolean gapEnabled,
        Boolean dispatchEnabled,
        Boolean dryRun
    ) {}

    public AiProperties(
        boolean gapEnabled,
        boolean dispatchEnabled,
        boolean dryRun,
        int debounceSeconds,
        int cooldownMinutes,
        int maxAsks,
        String defaultProvider,
        String activeModel,
        String apiKey,
        String baseUrl,
        int maxTokens,
        int timeoutSeconds
    ) {
        this(gapEnabled, dispatchEnabled, dryRun, debounceSeconds, cooldownMinutes, maxAsks,
             defaultProvider, activeModel, apiKey, baseUrl, maxTokens, timeoutSeconds, Map.of());
    }

    @org.springframework.boot.context.properties.bind.ConstructorBinding
    public AiProperties {
        debounceSeconds = debounceSeconds <= 0 ? 30 : debounceSeconds;
        cooldownMinutes = cooldownMinutes <= 0 ? 5 : cooldownMinutes;
        maxAsks = maxAsks <= 0 ? 3 : maxAsks;
        defaultProvider = (defaultProvider == null || defaultProvider.isBlank()) ? "Anthropic" : defaultProvider;
        activeModel = (activeModel == null || activeModel.isBlank()) ? "claude-haiku-4-5" : activeModel;
        apiKey = apiKey == null ? "" : apiKey;
        baseUrl = baseUrl == null ? "" : baseUrl;
        maxTokens = maxTokens <= 0 ? 4096 : maxTokens;
        timeoutSeconds = timeoutSeconds <= 0 ? 15 : timeoutSeconds;
        tenants = tenants == null ? Map.of() : Map.copyOf(tenants);
    }

    public static AiProperties defaults() {
        return new AiProperties(false, false, false, 30, 5, 3, "Anthropic", "claude-haiku-4-5", "", "", 4096, 15, Map.of());
    }

    public boolean isGapEnabledFor(String tenantId) {
        if (tenants != null && tenantId != null && tenants.containsKey(tenantId)) {
            Boolean val = tenants.get(tenantId).gapEnabled();
            if (val != null) return val;
        }
        return gapEnabled;
    }

    public boolean isDispatchEnabledFor(String tenantId) {
        if (tenants != null && tenantId != null && tenants.containsKey(tenantId)) {
            Boolean val = tenants.get(tenantId).dispatchEnabled();
            if (val != null) return val;
        }
        return dispatchEnabled;
    }

    public boolean isDryRunFor(String tenantId) {
        if (tenants != null && tenantId != null && tenants.containsKey(tenantId)) {
            Boolean val = tenants.get(tenantId).dryRun();
            if (val != null) return val;
        }
        return dryRun;
    }

    @Override
    public String toString() {
        return "AiProperties[gapEnabled=" + gapEnabled + ", dispatchEnabled=" + dispatchEnabled + ", dryRun=" + dryRun + ", provider=" + defaultProvider + ", model=" + activeModel + ", apiKey=<redacted>]";
    }
}
