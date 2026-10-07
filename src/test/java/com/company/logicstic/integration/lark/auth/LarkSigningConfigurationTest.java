package com.company.logicstic.integration.lark.auth;

import com.company.logicstic.integration.lark.config.LarkProperties;
import com.company.logicstic.repository.EmployeeRepository;
import com.company.logicstic.exception.ApiException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class LarkSigningConfigurationTest {
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods=false)
    @org.springframework.boot.context.properties.EnableConfigurationProperties(LarkProperties.class)
    static class BoundSigningConfiguration {}

    @Test void actualStartupBindingRejectsEnabledAuthenticationWithoutAnExplicitKey() {
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withUserConfiguration(BoundSigningConfiguration.class)
                .withPropertyValues("app.lark.enabled=true")
                .run(context -> assertNotNull(context.getStartupFailure()));
    }
    @Test void actualStartupBindingAcceptsExplicitKeyOrDisabledAuthentication() {
        var runner = new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withUserConfiguration(BoundSigningConfiguration.class);
        runner.withPropertyValues("app.lark.enabled=true", "app.lark.jwt-secret=fixture-startup-key-with-more-than-32-bytes")
                .run(context -> { assertNull(context.getStartupFailure()); assertTrue(context.getBean(LarkProperties.class).enabled()); });
        runner.withPropertyValues("app.lark.enabled=false")
                .run(context -> { assertNull(context.getStartupFailure()); assertNull(context.getBean(LarkProperties.class).jwtSecret()); });
    }
    private LarkProperties properties(boolean enabled,String key) {
        return new LarkProperties(enabled,null,null,null,null,key,null,Duration.ofHours(1),Duration.ofMinutes(10),null);
    }
    @Test void enabledAuthenticationRejectsAbsentShortAndKnownSharedKeys() {
        for(String key:new String[]{null,""," ","weak-key","logistics-lark-default-secret-key-at-least-256-bits-long-change-in-production!","your-secure-jwt-secret-key-at-least-256-bits"})
            assertThrows(IllegalArgumentException.class,()->properties(true,key));
    }
    @Test void distinctConfiguredKeysCannotVerifyEachOthersTokens() {
        var one=new LarkAuthService(properties(true,"fixture-key-one-with-more-than-32-bytes"),mock(LarkAuthClient.class),mock(EmployeeRepository.class));
        var two=new LarkAuthService(properties(true,"fixture-key-two-with-more-than-32-bytes"),mock(LarkAuthClient.class),mock(EmployeeRepository.class));
        String token=one.createInternalToken("subject","fixture@example.test","tenant",List.of("ADMIN"),"Fixture",null);
        assertEquals("subject",one.validateInternalToken(token).getSubject());
        assertThrows(io.jsonwebtoken.JwtException.class,()->two.validateInternalToken(token));
    }
    @Test void disabledAuthenticationNeedsNoSigningKeyAndCannotIssueOrVerifyTokens() {
        var service=new LarkAuthService(properties(false,null),mock(LarkAuthClient.class),mock(EmployeeRepository.class));
        assertThrows(ApiException.class,()->service.createInternalToken("subject","fixture@example.test","tenant",List.of("ADMIN"),"Fixture",null));
        assertThrows(ApiException.class,()->service.validateInternalToken("token"));
        assertThrows(ApiException.class,()->service.getAuthorizeUrl("/"));
    }
}
