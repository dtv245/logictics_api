package com.company.logicstic.integration.lark.auth;

import com.company.logicstic.exception.ApiException;
import com.company.logicstic.integration.lark.config.LarkProperties;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.web.client.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Distinguishes app credentials and upstream outages from end-user authentication. */
class LarkFailureContractTest {
    LarkProperties properties() {
        var p=mock(LarkProperties.class);when(p.appId()).thenReturn("app-fixture");when(p.appSecret()).thenReturn("secret-fixture");
        when(p.baseUrl()).thenReturn("https://provider.example.test");return p;
    }
    @Test void appCredentialFailureAndRateLimitAreNotUserUnauthorized() {
        for(int code: new int[]{10014,99991400}) {
            var http=mock(RestTemplate.class);
            when(http.exchange(anyString(),eq(HttpMethod.POST),any(HttpEntity.class),eq(LarkAuthClient.LarkAppTokenResponse.class)))
                    .thenReturn(ResponseEntity.ok(new LarkAuthClient.LarkAppTokenResponse(code,"private-provider-detail",null,null)));
            var client=new LarkAuthClient(properties(),http);
            assertThatThrownBy(client::getAppAccessToken).isInstanceOfSatisfying(ApiException.class,e->{
                assertThat(e.getStatus().value()).isEqualTo(code==10014?502:503);
                assertThat(e.getMessage()).doesNotContain("private-provider-detail");
                if(code==99991400) assertThat(e.getHeaders().getFirst("Retry-After")).isEqualTo("30");
            });
        }
    }
    @Test void timeoutConnectFailureAnd429HaveDifferentContracts() {
        var headers=new HttpHeaders();headers.set("Retry-After","45");
        RuntimeException[] failures={new ResourceAccessException("private-timeout",new SocketTimeoutException()),
                new ResourceAccessException("private-connect"),
                HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS,"private-provider",headers,"private-body".getBytes(),java.nio.charset.StandardCharsets.UTF_8)};
        for(int i=0;i<failures.length;i++) {
            var http=mock(RestTemplate.class);
            when(http.exchange(anyString(),eq(HttpMethod.POST),any(HttpEntity.class),eq(LarkAuthClient.LarkAppTokenResponse.class))).thenThrow(failures[i]);
            var client=new LarkAuthClient(properties(),http);int expected=i==0?504:503;
            assertThatThrownBy(client::getAppAccessToken).isInstanceOfSatisfying(ApiException.class,e->{
                assertThat(e.getStatus().value()).isEqualTo(expected);assertThat(e.getMessage()).doesNotContain("private-");
                if(e.getCode().equals("LARK_RATE_LIMITED")) assertThat(e.getHeaders().getFirst("Retry-After")).isEqualTo("45");
            });
        }
    }
    @Test void malformedSuccessfulResponseIs502() {
        var http=mock(RestTemplate.class);
        when(http.exchange(anyString(),eq(HttpMethod.POST),any(HttpEntity.class),eq(LarkAuthClient.LarkAppTokenResponse.class))).thenReturn(ResponseEntity.ok(null));
        assertThatThrownBy(new LarkAuthClient(properties(),http)::getAppAccessToken).isInstanceOfSatisfying(ApiException.class,
                e->assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }
    @Test void structuredCodesInHttpErrorsStillDistinguishCredentialOwnership() {
        var policy = new com.company.logicstic.integration.lark.LarkFailurePolicy(30);
        for (int code : new int[]{10014, 20003, 20004, 99991400}) {
            var failure = HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "provider-detail", new HttpHeaders(),
                    ("{\"code\":" + code + ",\"msg\":\"private-secret\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    java.nio.charset.StandardCharsets.UTF_8);
            var result = policy.transport(failure, com.company.logicstic.integration.lark.LarkFailurePolicy.Credential.CODE_EXCHANGE);
            assertThat(result.getStatus().value()).isEqualTo(code == 10014 ? 502 : code == 99991400 ? 503 : 401);
            assertThat(result.getMessage()).doesNotContain("private-secret");
            if (code == 10014) assertThat(result.getCode()).isEqualTo("LARK_APP_AUTH_FAILED");
        }
    }
    @Test void malformedCodesCannotOverrideHttpFailuresAndInvalidRetryHeaderUsesDefault() {
        var policy = new com.company.logicstic.integration.lark.LarkFailurePolicy(30);
        for (String body : new String[]{"not-json", "{\"code\":\"20003\"}", "{\"code\":20003.1}", "{\"code\":4294967299}"}) {
            var failure = HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "provider-detail", new HttpHeaders(),
                    body.getBytes(java.nio.charset.StandardCharsets.UTF_8), java.nio.charset.StandardCharsets.UTF_8);
            assertThat(policy.transport(failure, com.company.logicstic.integration.lark.LarkFailurePolicy.Credential.CODE_EXCHANGE).getStatus().value()).isEqualTo(502);
        }
        var headers = new HttpHeaders(); headers.set("Retry-After", "invalid");
        var rate = policy.body(99991400, com.company.logicstic.integration.lark.LarkFailurePolicy.Credential.APP, headers);
        assertThat(rate.getHeaders().getFirst("Retry-After")).isEqualTo("30");
        var outage = HttpServerErrorException.create(HttpStatus.SERVICE_UNAVAILABLE, "provider-detail", headers,
                "{\"code\":20003}".getBytes(java.nio.charset.StandardCharsets.UTF_8), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(policy.transport(outage, com.company.logicstic.integration.lark.LarkFailurePolicy.Credential.CODE_EXCHANGE).getStatus().value()).isEqualTo(503);
    }
}
