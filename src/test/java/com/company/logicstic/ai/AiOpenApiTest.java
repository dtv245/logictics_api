package com.company.logicstic.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:ai_openapi_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.flyway.enabled=false"
})
class AiOpenApiTest {

    @Autowired
    private WebApplicationContext context;

    @Test
    @DisplayName("OpenAPI v3 api-docs generates and exposes all AI Gap and AI Dispatch endpoints")
    void openApiIncludesAllAiEndpoints() throws Exception {
        var response = MockMvcBuilders.webAppContextSetup(context).build()
            .perform(get("/v3/api-docs"))
            .andReturn()
            .getResponse();

        assertEquals(200, response.getStatus());

        var json = JsonMapper.builder().build();
        var document = json.readTree(response.getContentAsString());
        var paths = document.path("paths");

        // Verify required AI endpoints from contract
        assertTrue(paths.has("/api/ai/features"), "Missing /api/ai/features");
        assertTrue(paths.has("/api/ai/loads/{loadId}/status"), "Missing /api/ai/loads/{loadId}/status");
        assertTrue(paths.has("/api/ai/loads/overview"), "Missing /api/ai/loads/overview");
        assertTrue(paths.has("/api/ai/loads/{loadId}/rerun"), "Missing /api/ai/loads/{loadId}/rerun");
        assertTrue(paths.has("/api/ai/runs"), "Missing /api/ai/runs");

        assertTrue(paths.has("/api/ai/gaps"), "Missing /api/ai/gaps");
        assertTrue(paths.has("/api/ai/gaps/check"), "Missing /api/ai/gaps/check");
        assertTrue(paths.has("/api/ai/gaps/{id}/dismiss"), "Missing /api/ai/gaps/{id}/dismiss");
        assertTrue(paths.has("/api/ai/gaps/{id}/answer"), "Missing /api/ai/gaps/{id}/answer");

        assertTrue(paths.has("/api/ai/dispatch/runs"), "Missing /api/ai/dispatch/runs");
        assertTrue(paths.has("/api/ai/dispatch/sessions"), "Missing /api/ai/dispatch/sessions");
        assertTrue(paths.has("/api/ai/dispatch/sessions/{id}"), "Missing /api/ai/dispatch/sessions/{id}");
        assertTrue(paths.has("/api/ai/dispatch/pending"), "Missing /api/ai/dispatch/pending");
        assertTrue(paths.has("/api/ai/dispatch/decisions/{id}/approve"), "Missing /api/ai/dispatch/decisions/{id}/approve");
        assertTrue(paths.has("/api/ai/dispatch/decisions/{id}/reject"), "Missing /api/ai/dispatch/decisions/{id}/reject");
        assertTrue(paths.has("/api/ai/dispatch/sessions/{id}/cancel"), "Missing /api/ai/dispatch/sessions/{id}/cancel");
        assertTrue(paths.has("/api/ai/config"), "Missing /api/ai/config");
    }
}
