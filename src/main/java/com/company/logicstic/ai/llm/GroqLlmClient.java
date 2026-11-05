package com.company.logicstic.ai.llm;

import com.company.logicstic.ai.config.AiProperties;
import com.company.logicstic.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** Temporary OpenAI-compatible Groq client, enabled only for the test profile. */
@Component
@Profile("groq-dispatch-test")
public class GroqLlmClient implements LlmClient {
    private static final String DEFAULT_BASE_URL = "https://api.groq.com/openai/v1";
    private static final String DEFAULT_MODEL = "openai/gpt-oss-20b";

    private final AiProperties properties;
    private final RestClient restClient;
    public GroqLlmClient(AiProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.create();
    }

    @Override
    public Response chat(Request request) {
        if (properties.apiKey().isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GROQ_API_KEY_MISSING",
                    "Thiếu AI_LLM_API_KEY cho profile groq-dispatch-test.");
        }
        String baseUrl = properties.baseUrl().isBlank() ? DEFAULT_BASE_URL : properties.baseUrl().trim();
        baseUrl = baseUrl.replaceAll("/+$", "");
        String model = request.model() == null || request.model().isBlank()
                ? (properties.activeModel().isBlank() ? DEFAULT_MODEL : properties.activeModel())
                : request.model();
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", request.systemPrompt()),
                        Map.of("role", "user", "content", request.userPrompt())),
                "temperature", request.temperature(),
                "max_tokens", request.maxTokens(),
                "response_format", Map.of("type", "json_object"));

        final JsonNode response;
        try {
            response = restClient.post()
                    .uri(baseUrl + "/chat/completions")
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "GROQ_PROVIDER_ERROR",
                    "Groq từ chối yêu cầu điều phối (HTTP " + ex.getStatusCode().value() + ").");
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "GROQ_PROVIDER_UNAVAILABLE",
                    "Không thể kết nối Groq để xếp hạng ứng viên điều phối.");
        }

        JsonNode choice = response == null ? null : response.path("choices").path(0);
        String content = choice == null ? "" : choice.path("message").path("content").asText("");
        if (content.isBlank()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "GROQ_EMPTY_RESPONSE",
                    "Groq trả về kết quả điều phối rỗng.");
        }
        JsonNode usage = response.path("usage");
        String returnedModel = response.path("model").asText(model);
        return new Response(content, List.of(), usage.path("prompt_tokens").asInt(0),
                usage.path("completion_tokens").asInt(0), returnedModel);
    }
}
