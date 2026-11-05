package com.company.logicstic.ai.llm;

import com.company.logicstic.exception.ApiException;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Production placeholder until a real provider is wired in Phase 3.
 *
 * <p>The deterministic fake is restricted to the {@code test} and {@code dev}
 * profiles. Outside those profiles this client makes the missing provider
 * explicit instead of silently returning synthetic rankings.</p>
 */
@Component
@Profile("!test & !dev & !groq-dispatch-test")
public class UnconfiguredLlmClient implements LlmClient {

    @Override
    public Response chat(Request request) {
        throw new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "AI_LLM_NOT_CONFIGURED",
                "AI LLM provider chưa được cấu hình cho môi trường này. "
                        + "Không có đề xuất AI nào được tạo từ dữ liệu giả.");
    }
}
