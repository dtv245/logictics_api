package com.company.logicstic.exception;

import com.company.logicstic.config.RequestIds;
import com.company.logicstic.dto.ApiError;
import com.company.logicstic.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.MethodValidationResult;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Maps transport and domain failures without hiding server defects as client faults. */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(Exception exception, HttpServletRequest request) {
        return failure(403, "FORBIDDEN", "Access is forbidden", List.of(), Map.of(), new HttpHeaders(), exception, request);
    }
    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(Exception exception, HttpServletRequest request) {
        return failure(401, "UNAUTHORIZED", "Authentication is required", List.of(), Map.of(), new HttpHeaders(), exception, request);
    }
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApiException(ApiException exception, HttpServletRequest request) {
        return failure(exception.getStatus().value(), exception.getCode(), exception.getMessage(), exception.getErrors(),
                exception.getParams(), exception.getHeaders(), exception, request);
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException exception, HttpServletRequest request) {
        var errors = exception.getBindingResult().getAllErrors().stream().map(error -> {
            Map<String,Object> params = Map.of();
            if (error.contains(jakarta.validation.ConstraintViolation.class)) {
                var violation = error.unwrap(jakarta.validation.ConstraintViolation.class);
                params = constraintParams(violation.getConstraintDescriptor().getAttributes());
            }
            return new ApiError(error instanceof FieldError field ? field.getField() : null,
                    error.getCode(), error.getDefaultMessage(), params);
        }).toList();
        return validation(errors, exception, request);
    }
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException exception, HttpServletRequest request) {
        // A constraint on a service/controller return value indicates a server defect.
        if (exception.getConstraintViolations().stream().anyMatch(v -> {
            for (var node : v.getPropertyPath()) if (node.getKind() == jakarta.validation.ElementKind.RETURN_VALUE) return true;
            return false;
        })) return internal(exception, request);
        var errors = exception.getConstraintViolations().stream().map(v -> new ApiError(
                v.getPropertyPath().toString(), v.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName(),
                v.getMessage(), constraintParams(v.getConstraintDescriptor().getAttributes()))).toList();
        return validation(errors, exception, request);
    }
    @ExceptionHandler({HandlerMethodValidationException.class, org.springframework.validation.method.MethodValidationException.class})
    public ResponseEntity<ApiResponse<Void>> handleMethodValidation(Exception exception, HttpServletRequest request) {
        var result = (MethodValidationResult) exception;
        if (result.isForReturnValue()) return internal(exception, request);
        List<ApiError> errors = new ArrayList<>();
        for (var parameter : result.getParameterValidationResults()) {
            var methodParameter = parameter.getMethodParameter();
            var query = methodParameter.getParameterAnnotation(RequestParam.class);
            var header = methodParameter.getParameterAnnotation(RequestHeader.class);
            String field = query != null && !query.name().isEmpty() ? query.name()
                    : query != null && !query.value().isEmpty() ? query.value()
                    : header != null && !header.name().isEmpty() ? header.name()
                    : header != null && !header.value().isEmpty() ? header.value() : methodParameter.getParameterName();
            for (var error : parameter.getResolvableErrors()) {
                String[] codes = error.getCodes();
                String code = codes == null || codes.length == 0 ? "INVALID_VALUE" : codes[codes.length - 1];
                Map<String,Object> params = Map.of();
                try {
                    var violation = parameter.unwrap(error, jakarta.validation.ConstraintViolation.class);
                    params = constraintParams(violation.getConstraintDescriptor().getAttributes());
                } catch (IllegalArgumentException ignored) { /* Global/cross-parameter errors need no rejected value. */ }
                errors.add(new ApiError(error instanceof FieldError f ? f.getField()
                        : parameter instanceof ParameterErrors ? null : field, code, error.getDefaultMessage(), params));
            }
        }
        for (var error : result.getCrossParameterValidationResults()) errors.add(new ApiError(null, "INVALID_VALUE", error.getDefaultMessage()));
        return validation(errors, exception, request);
    }
    private static Map<String,Object> constraintParams(Map<String,Object> attributes) {
        Map<String,Object> result = new LinkedHashMap<>();
        for (String key : List.of("min", "max", "value", "integer", "fraction")) {
            Object value = attributes.get(key);
            if (value instanceof Number || value instanceof String || value instanceof Boolean) result.put(key, value);
        }
        return result;
    }
    @ExceptionHandler(TypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(TypeMismatchException exception, HttpServletRequest request) {
        if (exception instanceof org.springframework.beans.ConversionNotSupportedException) return internal(exception, request);
        String field = exception instanceof MethodArgumentTypeMismatchException argument ? argument.getName() : exception.getPropertyName();
        var params = exception.getRequiredType() == null ? Map.<String,Object>of() : Map.<String,Object>of("expectedType", exception.getRequiredType().getSimpleName());
        return failure(400, "MALFORMED_REQUEST", "Request parameter has an invalid type",
                List.of(new ApiError(field, "TYPE_MISMATCH", "Value has an invalid type", params)), Map.of(), new HttpHeaders(), exception, request);
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableMessage(HttpMessageNotReadableException exception, HttpServletRequest request) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        String field = null;
        for (Throwable cause = exception.getCause(); cause != null && seen.add(cause); cause = cause.getCause()) {
            if (cause instanceof ApiException domain) return handleApiException(domain, request);
            if (cause instanceof tools.jackson.databind.exc.MismatchedInputException mismatch && !mismatch.getPath().isEmpty()) {
                StringBuilder path = new StringBuilder();
                for (var reference : mismatch.getPath()) {
                    if (reference.getPropertyName() != null) {
                        if (!path.isEmpty()) path.append('.');
                        path.append(reference.getPropertyName());
                    } else if (reference.getIndex() >= 0) path.append('[').append(reference.getIndex()).append(']');
                }
                if (!path.isEmpty()) field = path.toString();
            }
        }
        String detail = field == null ? "Request body is missing or malformed" : "Body field has an invalid type";
        var errors = field == null ? List.<ApiError>of() : List.of(new ApiError(field, "TYPE_MISMATCH", detail));
        return failure(400, "MALFORMED_REQUEST", detail, errors, Map.of(), new HttpHeaders(), exception, request);
    }
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(org.springframework.dao.DataIntegrityViolationException exception, HttpServletRequest request) {
        return handleDatabaseFailure(exception, request);
    }
    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleDatabaseFailure(Exception exception, HttpServletRequest request) {
        var result = DatabaseFailures.classify(exception, request);
        // Structured identifiers are internal diagnostics, never response fields or rejected row text.
        log.atLevel(result.status() >= 500 ? org.slf4j.event.Level.ERROR : org.slf4j.event.Level.INFO)
                .log("database_failure sqlState={} constraint={} requestId={}", result.sqlState(), result.constraint(), RequestIds.getOrCreate(request));
        return failure(result.status(), result.code(), result.message(), result.errors(), result.params(), new HttpHeaders(), exception, request);
    }
    @ExceptionHandler({jakarta.persistence.OptimisticLockException.class, org.springframework.orm.ObjectOptimisticLockingFailureException.class})
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLockException(Exception exception, HttpServletRequest request) {
        return failure(409, "VERSION_CONFLICT", "The resource has been updated by another transaction. Please reload and retry.",
                List.of(), Map.of("conflictType", "VERSION", "legacyCode", "CONCURRENT_MODIFICATION_CONFLICT"),
                new HttpHeaders(), exception, request);
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception, HttpServletRequest request) {
        if (exception instanceof org.springframework.dao.DataAccessException || DatabaseFailures.hasSqlCause(exception))
            return handleDatabaseFailure(exception, request);
        // ErrorResponse carries the actual HTTP status and protocol headers for built-in Spring faults.
        if (exception instanceof ErrorResponse error) {
            int status = error.getStatusCode().value();
            if (status >= 500) return failure(status,
                    status == 503 ? "SERVICE_UNAVAILABLE" : status == 504 ? "REQUEST_TIMEOUT" : "INTERNAL_ERROR",
                    status == 503 ? "Service is temporarily unavailable" : "An unexpected error occurred",
                    List.of(), Map.of(), error.getHeaders(), exception, request);
            String code = switch (status) {
                case 400 -> "MALFORMED_REQUEST"; case 404 -> "RESOURCE_NOT_FOUND";
                case 405 -> "METHOD_NOT_ALLOWED"; case 406 -> "NOT_ACCEPTABLE";
                case 413 -> "PAYLOAD_TOO_LARGE"; case 415 -> "UNSUPPORTED_MEDIA_TYPE";
                case 401 -> "UNAUTHORIZED"; case 403 -> "FORBIDDEN"; default -> "HTTP_" + status;
            };
            String message = switch (status) {
                case 404 -> "Requested resource was not found"; case 405 -> "HTTP method is not supported";
                case 406 -> "Requested response format is not supported"; case 413 -> "Request payload is too large";
                case 415 -> "Request content type is not supported"; default -> "Request cannot be processed";
            };
            List<ApiError> errors = List.of();
            if (exception instanceof MissingServletRequestParameterException missing) {
                code = "MISSING_REQUEST_PARAMETER"; message = "Required request parameter is missing";
                errors = List.of(new ApiError(missing.getParameterName(), "REQUIRED", "Value is required"));
            } else if (exception instanceof MissingRequestHeaderException missing) {
                code = "MISSING_REQUEST_HEADER"; message = "Required request header is missing";
                errors = List.of(new ApiError(missing.getHeaderName(), "REQUIRED", "Value is required"));
            }
            return failure(status, code, message, errors, Map.of(), error.getHeaders(), exception, request);
        }
        return internal(exception, request);
    }
    private ResponseEntity<ApiResponse<Void>> validation(List<ApiError> errors, Exception exception, HttpServletRequest request) {
        return failure(400, "VALIDATION_FAILED", "Request validation failed", errors, Map.of(), new HttpHeaders(), exception, request);
    }
    private ResponseEntity<ApiResponse<Void>> internal(Exception exception, HttpServletRequest request) {
        return failure(500, "INTERNAL_ERROR", "An unexpected error occurred", List.of(), Map.of(), new HttpHeaders(), exception, request);
    }
    private ResponseEntity<ApiResponse<Void>> failure(int status, String code, String message, List<ApiError> errors,
            Map<String,Object> params, HttpHeaders headers, Exception exception, HttpServletRequest request) {
        ErrorLog.write(log, status, code, exception, request);
        return ResponseEntity.status(status).headers(headers).header(RequestIds.HEADER, RequestIds.getOrCreate(request))
                .header("Cache-Control", "no-store").body(ApiResponse.failure(code, message, errors, params, request));
    }
}
