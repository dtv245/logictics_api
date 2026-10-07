package com.company.logicstic.config;

import com.company.logicstic.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import tools.jackson.databind.json.JsonMapper;

public final class SecurityResponses {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private SecurityResponses() { }
    public static void write(HttpServletRequest request, HttpServletResponse response,
                             int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        JSON.writeValue(response.getOutputStream(), ApiResponse.failure(code, message, List.of(), request));
    }
}
