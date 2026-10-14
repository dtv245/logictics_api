package com.company.logicstic.integration.lark;

import com.company.logicstic.exception.ApiException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import tools.jackson.databind.json.JsonMapper;

/** Classifies provider failures by credential ownership, never by raw provider messages. */
@Component
public class LarkFailurePolicy {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    public enum Credential { APP, CODE_EXCHANGE, USER_TOKEN, BASE }
    private final int defaultWait;
    public LarkFailurePolicy(@Value("${app.lark.rate-limit-retry-after-seconds:30}") int defaultWait) {
        if(defaultWait<1||defaultWait>3600) throw new IllegalArgumentException("Lark retry interval must be between 1 and 3600 seconds");
        this.defaultWait=defaultWait;
    }
    public ApiException body(Integer providerCode, Credential credential, HttpHeaders headers) {
        if(providerCode!=null && providerCode==99991400) return rateLimited(headers);
        if(providerCode!=null && Set.of(10014,20002,20014,99991543,99991662,99991673).contains(providerCode))
            return failure(HttpStatus.BAD_GATEWAY,"LARK_APP_AUTH_FAILED","The service could not authenticate its Lark application");
        // Legacy authen/v1: these codes specifically identify a rejected/expired user code.
        if(credential==Credential.CODE_EXCHANGE && providerCode!=null && Set.of(20003,20004).contains(providerCode))
            return failure(HttpStatus.UNAUTHORIZED,"UNAUTHORIZED","Lark authorization code is invalid or expired");
        if(credential==Credential.USER_TOKEN && providerCode!=null && Set.of(20005,20006,99991668,99991677).contains(providerCode))
            return failure(HttpStatus.UNAUTHORIZED,"UNAUTHORIZED","Lark user token is invalid or expired");
        return invalidResponse();
    }
    public ApiException transport(RestClientException exception, Credential credential) {
        if(exception instanceof RestClientResponseException response) {
            int status=response.getStatusCode().value();
            if(status==429) return rateLimited(response.getResponseHeaders());
            if(status==504||status==408) return failure(HttpStatus.GATEWAY_TIMEOUT,"LARK_TIMEOUT","Lark did not respond in time");
            if(status>=500) return failure(HttpStatus.SERVICE_UNAVAILABLE,"LARK_UNAVAILABLE","Lark is temporarily unavailable");
            // Some provider endpoints send the same structured code with HTTP 400.
            // Only bounded integer codes participate; provider messages remain private.
            Integer providerCode = providerCode(response);
            if(providerCode != null && providerCode != 0) {
                var classified = body(providerCode, credential, response.getResponseHeaders());
                if(!classified.getCode().equals("LARK_UPSTREAM_INVALID_RESPONSE")) return classified;
            }
            if(status==401) return credential==Credential.USER_TOKEN
                    ? failure(HttpStatus.UNAUTHORIZED,"UNAUTHORIZED","Lark user token is invalid or expired")
                    : failure(HttpStatus.BAD_GATEWAY,"LARK_APP_AUTH_FAILED","The service could not authenticate its Lark application");
            return invalidResponse();
        }
        Set<Throwable> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        for(Throwable cause=exception;cause!=null&&seen.add(cause);cause=cause.getCause()) {
            if(cause instanceof SocketTimeoutException||cause instanceof HttpTimeoutException)
                return failure(HttpStatus.GATEWAY_TIMEOUT,"LARK_TIMEOUT","Lark did not respond in time");
        }
        if(exception instanceof ResourceAccessException) return failure(HttpStatus.SERVICE_UNAVAILABLE,"LARK_UNAVAILABLE","Lark is temporarily unavailable");
        return invalidResponse();
    }
    private Integer providerCode(RestClientResponseException response) {
        byte[] body = response.getResponseBodyAsByteArray();
        if(body.length == 0 || body.length > 16384) return null;
        try {
            var root = JSON.readTree(body);
            var code = root == null ? null : root.get("code");
            return code != null && code.isIntegralNumber() && code.canConvertToInt() ? code.intValue() : null;
        } catch(tools.jackson.core.JacksonException ignored) {
            return null;
        }
    }
    public ApiException invalidResponse() {
        return failure(HttpStatus.BAD_GATEWAY,"LARK_UPSTREAM_INVALID_RESPONSE","Lark returned an invalid response");
    }
    private ApiException failure(HttpStatus status,String code,String message) { return new ApiException(status,code,message); }
    private ApiException rateLimited(HttpHeaders headers) {
        int wait=defaultWait;
        String raw=headers==null?null:headers.getFirst("Retry-After");
        if(raw!=null&&raw.length()<=100) try {
            long seconds=raw.matches("[0-9]{1,9}")?Long.parseLong(raw)
                    : Duration.between(Instant.now(),ZonedDateTime.parse(raw,DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).getSeconds();
            if(seconds>=0&&seconds<=86400)wait=(int)seconds;
        } catch(IllegalArgumentException|java.time.DateTimeException ignored) { /* Use the configured wait for untrusted headers. */ }
        final int retryAfter=wait;
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"LARK_RATE_LIMITED","Lark rate limit was reached",List.of(),Map.of("retryAfterSeconds",retryAfter)) {
            @Override public HttpHeaders getHeaders(){var result=new HttpHeaders();result.set("Retry-After",String.valueOf(retryAfter));return result;}
        };
    }
}
