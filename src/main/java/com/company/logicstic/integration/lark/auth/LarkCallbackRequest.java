package com.company.logicstic.integration.lark.auth;

public record LarkCallbackRequest(
    String code,
    String state,
    String error,
    String errorDescription,
    String returnTo) {
    @com.fasterxml.jackson.annotation.JsonIgnore
    @io.swagger.v3.oas.annotations.media.Schema(hidden=true)
    @jakarta.validation.constraints.AssertTrue(message="Supply code and state, or a provider error")
    public boolean isAuthorizationOutcomeValid() {
        return error != null && !error.isBlank()
                || code != null && !code.isBlank() && state != null && !state.isBlank();
    }
}
