package com.company.logicstic.ai.llm;

import java.util.List;
import java.util.Map;

public interface LlmClient {

    record ToolCall(
        String id,
        String name,
        Map<String, Object> arguments
    ) {}

    record Request(
        String systemPrompt,
        String userPrompt,
        List<Map<String, Object>> tools,
        double temperature,
        int maxTokens,
        String model
    ) {}

    record Response(
        String text,
        List<ToolCall> toolCalls,
        int inputTokens,
        int outputTokens,
        String model
    ) {}

    Response chat(Request request);

    /**
     * True when token counts returned by this client are fixtures rather than
     * provider usage. Callers must not persist them as real cost data.
     */
    default boolean reportsSyntheticUsage() {
        return false;
    }
}
