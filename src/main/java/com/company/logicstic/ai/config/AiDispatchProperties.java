package com.company.logicstic.ai.config;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration boundary for the v1 dispatcher; absent operational limits stay unset. */
@ConfigurationProperties(prefix = "ai.dispatch")
public record AiDispatchProperties(
        boolean autoEnabled,
        /** Explicit opt-in before an AI suggestion may be executed. Defaults to off. */
        boolean approvalEnabled,
        Integer maxSteps,
        Integer maxToolCalls,
        Integer maxToolOutputChars,
        Duration sessionTimeout,
        Duration decisionTtl,
        Duration llmTimeout,
        Integer maxTokens,
        BigDecimal temperature,
        String defaultProvider,
        List<String> fallbackOrder,
        Map<String, ProviderProperties> providers,
        Map<String, Object> pricing) {

    public AiDispatchProperties {
        fallbackOrder = fallbackOrder == null ? List.of() : List.copyOf(fallbackOrder);
        providers = providers == null ? Map.of() : Map.copyOf(providers);
        pricing = pricing == null ? Map.of() : Map.copyOf(pricing);
    }

    public record ProviderProperties(String type, String baseUrl, String apiKey, String model) {
        @Override
        public String toString() {
            return "ProviderProperties[type=" + type + ", baseUrl=<redacted>"
                    + ", apiKey=<redacted>, model=" + model + "]";
        }
    }

    @Override
    public String toString() {
        return "AiDispatchProperties[autoEnabled=" + autoEnabled + ", approvalEnabled=" + approvalEnabled
                + ", maxSteps=" + maxSteps
                + ", maxToolCalls=" + maxToolCalls + ", maxToolOutputChars=" + maxToolOutputChars
                + ", sessionTimeout=" + sessionTimeout + ", decisionTtl=" + decisionTtl
                + ", llmTimeout=" + llmTimeout + ", maxTokens=" + maxTokens
                + ", temperature=" + temperature + ", defaultProvider=" + defaultProvider
                + ", fallbackOrder=" + fallbackOrder + ", configuredProviders=" + providers.keySet()
                + ", providerCredentials=<redacted>, pricing=<redacted>]";
    }
}
