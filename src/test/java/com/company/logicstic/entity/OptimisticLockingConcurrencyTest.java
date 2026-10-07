package com.company.logicstic.entity;

import com.company.logicstic.dto.ApiResponse;
import com.company.logicstic.exception.GlobalExceptionHandler;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.Version;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

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
        assertEquals("CONCURRENT_MODIFICATION_CONFLICT", response.getBody().code());

        ObjectOptimisticLockingFailureException springEx =
                new ObjectOptimisticLockingFailureException(Load.class, "123");
        ResponseEntity<ApiResponse<Void>> springResponse = handler.handleOptimisticLockException(springEx, request);

        assertEquals(HttpStatus.CONFLICT, springResponse.getStatusCode());
        assertNotNull(springResponse.getBody());
        assertEquals("CONCURRENT_MODIFICATION_CONFLICT", springResponse.getBody().code());
    }
}
