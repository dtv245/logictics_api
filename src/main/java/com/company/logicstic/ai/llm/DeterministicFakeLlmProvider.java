package com.company.logicstic.ai.llm;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@Profile({"test", "dev"})
@Slf4j
public class DeterministicFakeLlmProvider implements LlmClient {

    @Setter
    private boolean simulateError = false;

    @Setter
    private boolean simulateTimeout = false;

    @Setter
    private String customResponse = null;

    @Override
    public boolean reportsSyntheticUsage() {
        return true;
    }

    @Override
    public Response chat(Request request) {
        log.info("DeterministicFakeLlmProvider chat called model={}", request.model());

        if (simulateTimeout) {
            throw new RuntimeException("LLM_PROVIDER_TIMEOUT: Simulated timeout");
        }
        if (simulateError) {
            throw new RuntimeException("LLM_PROVIDER_ERROR: Simulated failure");
        }

        if (customResponse != null) {
            return new Response(customResponse, List.of(), 100, 50, request.model());
        }

        // Check if this is a Gap Natural Question rewrite request
        if (request.systemPrompt() != null && request.systemPrompt().contains("GAP_REWRITE")) {
            String naturalQuestion = "Hệ thống phát hiện thông tin đơn hàng chưa đầy đủ. Bạn vui lòng kiểm tra và cập nhật các trường còn thiếu nhé!";
            return new Response(naturalQuestion, List.of(), 120, 45, request.model());
        }

        // Default response for AI Dispatch re-ranking
        String jsonRanking = """
            {
              "summary": "Đã phân tích các ứng viên khả thi từ Optimization Engine và xếp hạng lại tối ưu theo khoảng cách và giờ HOS.",
              "rankings": [
                {
                  "rank": 1,
                  "confidence": "HIGH",
                  "reasoning": "Tài xế có vị trí gần nhất, giờ lái xe an toàn vượt tiêu chuẩn và đạt biên lợi nhuận cao."
                }
              ]
            }
            """;

        return new Response(jsonRanking, List.of(), 250, 110, request.model());
    }
}
