package com.company.logicstic.api.response;

import com.company.logicstic.dto.ApiError;
import com.company.logicstic.dto.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiResponseTests {

    @Test
    void shouldCreateSuccessfulEnvelope() {
        MockHttpServletRequest request = request();

        ApiResponse<String> response = ApiResponse.success("payload", request);

        assertTrue(response.success());
        assertEquals("OK", response.code());
        assertEquals("payload", response.data());
        assertTrue(response.errors().isEmpty());
        assertEquals("/api/test", response.meta().path());
        assertEquals("request-123", response.meta().requestId());
    }

    @Test
    void shouldCreateImmutableFailureEnvelopeWithoutData() {
        MockHttpServletRequest request = request();
        List<ApiError> source = new java.util.ArrayList<>();
        source.add(new ApiError("name", "NotBlank", "must not be blank"));

        ApiResponse<Void> response = ApiResponse.failure(
                "VALIDATION_FAILED",
                "Request validation failed",
                source,
                request
        );
        source.clear();

        assertFalse(response.success());
        assertNull(response.data());
        assertEquals(1, response.errors().size());
        assertThrows(UnsupportedOperationException.class, () -> response.errors().clear());
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        request.addHeader("X-Request-Id", "request-123");
        return request;
    }
}
