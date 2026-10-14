package com.company.logicstic.exception;

import com.company.logicstic.dto.ApiError;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ConflictUniqueFieldContractTest {
    @Test
    void factoryReusesDuplicateConstructorAndPreservesUserMessage() {
        var exception = ConflictException.forUniqueField("number", "Truck number already exists");

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("DUPLICATE_VALUE", exception.getCode());
        assertEquals("Truck number already exists", exception.getMessage());
        assertEquals(List.of(new ApiError("number", "UNIQUE", "This value is already in use")), exception.getErrors());
        assertTrue(exception.getParams().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"number", "email", "name"})
    void factoryProducesFieldErrorInHttpEnvelope(String field) throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new Probe())
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        mvc.perform(get("/unique-conflict").param("field", field).header("X-Request-Id", "unique-field-contract"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("DUPLICATE_VALUE"))
                .andExpect(jsonPath("$.message").value("This input is already registered"))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value(field))
                .andExpect(jsonPath("$.errors[0].code").value("UNIQUE"))
                .andExpect(jsonPath("$.errors[0].message").value("This value is already in use"))
                .andExpect(jsonPath("$.params").isMap())
                .andExpect(jsonPath("$.params").isEmpty())
                .andExpect(jsonPath("$.meta.path").value("/unique-conflict"))
                .andExpect(jsonPath("$.meta.requestId").value("unique-field-contract"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"OPTIMIZATION_IDEMPOTENCY_CONFLICT", "INVOICE_HISTORY_IMMUTABLE", "SETTLEMENT_REVENUE_BASIS_STALE"})
    void commandConflictKeepsCodeMessageAndEmptyErrors(String code) throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new Probe())
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        mvc.perform(get("/command-conflict").param("code", code))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").value("Explicit command resolution required"))
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(jsonPath("$.params").isEmpty());
    }

    @RestController
    static class Probe {
        @GetMapping("/unique-conflict")
        String unique(@RequestParam String field) {
            throw ConflictException.forUniqueField(field, "This input is already registered");
        }

        @GetMapping("/command-conflict")
        String command(@RequestParam String code) {
            throw new ConflictException(code, "Explicit command resolution required");
        }
    }
}
