package com.company.logicstic.ai.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.*;

class AiDispatchPropertiesTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(AiDispatchConfiguration.class);

    @Test
    void defaultsFailClosedAndDoNotInventProviderModelOrPricing() {
        runner.run(context -> {
            assertNull(context.getStartupFailure());
            AiDispatchProperties properties = context.getBean(AiDispatchProperties.class);
            assertFalse(properties.autoEnabled());
            assertFalse(properties.approvalEnabled());
            assertNull(properties.defaultProvider());
            assertTrue(properties.providers().isEmpty());
            assertTrue(properties.pricing().isEmpty());
            assertNull(properties.maxSteps());
        });
    }

    @Test
    void bindsRequiredDispatcherKeysAndRedactsProviderSecretFromToString() {
        String secret = "test-only-provider-key";
        runner.withPropertyValues(
                "ai.dispatch.auto-enabled=true",
                "ai.dispatch.approval-enabled=true",
                "ai.dispatch.max-steps=7",
                "ai.dispatch.max-tool-calls=12",
                "ai.dispatch.max-tool-output-chars=4000",
                "ai.dispatch.session-timeout=45s",
                "ai.dispatch.decision-ttl=15m",
                "ai.dispatch.llm-timeout=20s",
                "ai.dispatch.max-tokens=2048",
                "ai.dispatch.temperature=0.2",
                "ai.dispatch.default-provider=provider-a",
                "ai.dispatch.fallback-order[0]=provider-b",
                "ai.dispatch.providers.provider-a.type=anthropic-messages",
                "ai.dispatch.providers.provider-a.base-url=https://example.invalid",
                "ai.dispatch.providers.provider-a.api-key=" + secret,
                "ai.dispatch.providers.provider-a.model=model-from-config",
                "ai.dispatch.pricing.test-rate=0")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    AiDispatchProperties properties = context.getBean(AiDispatchProperties.class);
                    assertTrue(properties.autoEnabled());
                    assertTrue(properties.approvalEnabled());
                    assertEquals(7, properties.maxSteps());
                    assertEquals(12, properties.maxToolCalls());
                    assertEquals(4000, properties.maxToolOutputChars());
                    assertEquals(java.time.Duration.ofSeconds(45), properties.sessionTimeout());
                    assertEquals(java.time.Duration.ofMinutes(15), properties.decisionTtl());
                    assertEquals(java.time.Duration.ofSeconds(20), properties.llmTimeout());
                    assertEquals(2048, properties.maxTokens());
                    assertEquals(new java.math.BigDecimal("0.2"), properties.temperature());
                    assertEquals("provider-a", properties.defaultProvider());
                    assertEquals("provider-b", properties.fallbackOrder().getFirst());
                    assertEquals("anthropic-messages", properties.providers().get("provider-a").type());
                    assertEquals("https://example.invalid", properties.providers().get("provider-a").baseUrl());
                    assertEquals(secret, properties.providers().get("provider-a").apiKey());
                    assertEquals("model-from-config", properties.providers().get("provider-a").model());
                    assertTrue(properties.pricing().containsKey("test-rate"));
                    assertFalse(properties.toString().contains(secret));
                    assertFalse(properties.providers().get("provider-a").toString().contains(secret));
                    assertFalse(properties.providers().get("provider-a").toString().contains("example.invalid"));
                });
    }
}
