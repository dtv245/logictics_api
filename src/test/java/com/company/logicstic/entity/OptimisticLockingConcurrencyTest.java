package com.company.logicstic.entity;

import com.company.logicstic.common.ExpectedVersionGuard;
import com.company.logicstic.dto.ApiResponse;
import com.company.logicstic.exception.BadRequestException;
import com.company.logicstic.exception.GlobalExceptionHandler;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.Version;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OptimisticLockingConcurrencyTest {

    @Test
    void coreEntitiesHaveVersionAnnotation() {
        Class<?>[] entities = {Load.class, Trip.class, Truck.class, Payment.class};
        for (Class<?> entityClass : entities) {
            boolean hasVersion = Arrays.stream(entityClass.getDeclaredFields())
                    .anyMatch(field -> field.isAnnotationPresent(Version.class));
            assertTrue(hasVersion, "Entity " + entityClass.getSimpleName() + " must have @Version field");
        }
    }

    @Test
    void globalExceptionHandlerTranslatesOptimisticLockExceptionTo409() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/loads/123");

        OptimisticLockException jpaEx = new OptimisticLockException("Row was updated or deleted by another transaction");
        ResponseEntity<ApiResponse<Void>> response = handler.handleOptimisticLockException(jpaEx, request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("VERSION_CONFLICT", response.getBody().code());
        assertFalse(response.getBody().success());
        assertTrue(response.getBody().errors().isEmpty());
        assertEquals("VERSION", response.getBody().params().get("conflictType"));
        assertEquals("CONCURRENT_MODIFICATION_CONFLICT", response.getBody().params().get("legacyCode"));

        ObjectOptimisticLockingFailureException springEx =
                new ObjectOptimisticLockingFailureException(Load.class, "123");
        ResponseEntity<ApiResponse<Void>> springResponse = handler.handleOptimisticLockException(springEx, request);

        assertEquals(HttpStatus.CONFLICT, springResponse.getStatusCode());
        assertNotNull(springResponse.getBody());
        assertEquals("VERSION_CONFLICT", springResponse.getBody().code());
        assertFalse(springResponse.getBody().success());
        assertTrue(springResponse.getBody().errors().isEmpty());
        assertEquals("VERSION", springResponse.getBody().params().get("conflictType"));
        assertEquals("CONCURRENT_MODIFICATION_CONFLICT", springResponse.getBody().params().get("legacyCode"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"jpa", "spring", "expectedVersion"})
    void versionConflictSourcesProduceTheSameHttpContract(String source) throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new VersionConflictController())
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        mvc.perform(put("/version-conflict/{source}", source))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(jsonPath("$.params.conflictType").value("VERSION"))
                .andExpect(jsonPath("$.params.legacyCode").value("CONCURRENT_MODIFICATION_CONFLICT"));
    }

    @Test
    void expectedVersionGuardStillAcceptsCurrentVersionsAndRejectsMissingOrNegativeVersions() {
        assertDoesNotThrow(() -> ExpectedVersionGuard.require(0L, 0L));
        assertDoesNotThrow(() -> ExpectedVersionGuard.require(2L, 2L));
        for (Long expected : Arrays.asList(null, -1L)) {
            var exception = assertThrows(BadRequestException.class, () -> ExpectedVersionGuard.require(expected, 2L));
            assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
            assertEquals("EXPECTED_VERSION_REQUIRED", exception.getCode());
        }
    }

    @RestController
    static class VersionConflictController {
        @PutMapping("/version-conflict/{source}")
        void update(@PathVariable("source") String source) {
            switch (source) {
                case "jpa" -> throw new OptimisticLockException("Internal persistence detail");
                case "spring" -> throw new ObjectOptimisticLockingFailureException(Load.class, "123");
                case "expectedVersion" -> ExpectedVersionGuard.require(1L, 2L);
                default -> throw new AssertionError("Unknown test source: " + source);
            }
        }
    }
}
