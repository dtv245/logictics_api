package com.company.logicstic.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Contracts for HTTP faults that previously fell through to INTERNAL_ERROR. */
class HttpErrorContractTest {
    MockMvc mvc;
    @BeforeEach void setup() { mvc = MockMvcBuilders.standaloneSetup(new Probe())
            .setControllerAdvice(new GlobalExceptionHandler()).build(); }
    @RestController static class Probe {
        record Input(@NotBlank String name, Integer capacity) {}
        @GetMapping("/probe/{id}") String item(@PathVariable java.util.UUID id) { return "ok"; }
        @GetMapping("/required") String required(@RequestParam String name) { return name; }
        @GetMapping("/header") String header(@RequestHeader("X-Required") String name) { return name; }
        @GetMapping("/minimum") String minimum(@RequestParam @Min(1) int count) { return "ok"; }
        @GetMapping("/output") @Min(1) int output() { return 0; }
        @PostMapping("/body") Input body(@Valid @RequestBody Input input) { return input; }
        @GetMapping("/upload") String upload() { throw new MaxUploadSizeExceededException(100); }
        @GetMapping("/domain") String domain() { throw new ConflictException("PAYMENT_IDEMPOTENCY_CONFLICT", "Approved domain message"); }
        @GetMapping("/currency") String currency() { throw new ApiException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, "CURRENCY_MISMATCH", "Currency does not match"); }
        @GetMapping("/unexpected") String unexpected() { throw new IllegalArgumentException("private-fixture-detail"); }
    }
    @ParameterizedTest @CsvSource({"/probe/not-a-uuid,400,MALFORMED_REQUEST", "/required,400,MISSING_REQUEST_PARAMETER",
            "/header,400,MISSING_REQUEST_HEADER", "/minimum?count=0,400,VALIDATION_FAILED",
            "/absent,404,RESOURCE_NOT_FOUND", "/upload,413,PAYLOAD_TOO_LARGE", "/output,500,INTERNAL_ERROR"})
    void requestFaultsKeepHttpSemantics(String path,int status,String code) throws Exception {
        mvc.perform(get(path).header("X-Request-Id","contract-fixture"))
                .andExpect(status().is(status)).andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(code)).andExpect(jsonPath("$.meta.requestId").value("contract-fixture"));
    }
    @Test void methodFaultKeepsAllowHeader() throws Exception {
        mvc.perform(patch("/body")).andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", org.hamcrest.Matchers.containsString("POST")));
    }
    @Test void unsupportedContentType() throws Exception {
        mvc.perform(post("/body").contentType("text/plain").content("{}"))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }
    @Test void bodyTypeMismatchIdentifiesFieldWithoutRejectedValue() throws Exception {
        mvc.perform(post("/body").contentType("application/json").content("{\"name\":\"Test\",\"capacity\":\"secret-rejected-value\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("capacity"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret-rejected-value"))));
    }
    @Test void existingDomainAndValidationContractsRemainValid() throws Exception {
        mvc.perform(get("/domain")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PAYMENT_IDEMPOTENCY_CONFLICT"));
        mvc.perform(get("/currency")).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("CURRENCY_MISMATCH"));
        mvc.perform(post("/body").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("name"));
        mvc.perform(get("/unexpected")).andExpect(status().isInternalServerError())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private-fixture-detail"))));
    }
}
