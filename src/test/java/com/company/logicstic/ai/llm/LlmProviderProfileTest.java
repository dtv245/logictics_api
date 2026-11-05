package com.company.logicstic.ai.llm;

import com.company.logicstic.exception.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the boundary that keeps fixture LLM output out of real environments.
 */
class LlmProviderProfileTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(DeterministicFakeLlmProvider.class, UnconfiguredLlmClient.class);

    @Test
    @DisplayName("test profile exposes the deterministic fake and marks its usage as synthetic")
    void fakeProviderActiveInTestProfile() {
        runner.withPropertyValues("spring.profiles.active=test").run(context -> {
            assertNull(context.getStartupFailure());
            LlmClient client = context.getBean(LlmClient.class);
            assertInstanceOf(DeterministicFakeLlmProvider.class, client);
            assertTrue(client.reportsSyntheticUsage());
        });
    }

    @Test
    @DisplayName("dev profile keeps the deterministic fake")
    void fakeProviderActiveInDevProfile() {
        runner.withPropertyValues("spring.profiles.active=dev").run(context -> {
            assertNull(context.getStartupFailure());
            assertInstanceOf(DeterministicFakeLlmProvider.class, context.getBean(LlmClient.class));
        });
    }

    @Test
    @DisplayName("production profile has no fake provider and fails closed with AI_LLM_NOT_CONFIGURED")
    void unconfiguredClientActiveOutsideTestAndDev() {
        runner.withPropertyValues("spring.profiles.active=prod").run(context -> {
            assertNull(context.getStartupFailure());
            LlmClient client = context.getBean(LlmClient.class);
            assertInstanceOf(UnconfiguredLlmClient.class, client);
            assertFalse(client.reportsSyntheticUsage());

            ApiException ex = assertThrows(ApiException.class, () -> client.chat(new LlmClient.Request(
                "system", "user", List.of(), 0.2, 1024, "claude-haiku-4-5")));
            assertEquals("AI_LLM_NOT_CONFIGURED", ex.getCode());
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
        });
    }
}
