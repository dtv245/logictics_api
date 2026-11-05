package com.company.logicstic.integration.lark.base;

import com.company.logicstic.integration.lark.*;
import com.company.logicstic.integration.lark.config.LarkProperties;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.http.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class LarkSyncGatewayTest {
  LarkProperties properties() {
    return new LarkProperties(false,"fixture-app","fixture-credential",null,"https://lark.invalid",null,null,null,null,null);
  }
  @Test void retriesRateLimitWithSameProviderIdempotencyTokenAndNoExtraSuccessfulWrite() {
    var mapping=new LarkSyncMapping(LarkSyncMappingTest.environment());var tokens=mock(LarkBaseTokenProvider.class);
    when(tokens.getTenantAccessToken()).thenReturn("fixture-token");
    var http=new RestTemplate();var server=MockRestServiceServer.bindTo(http).build();
    var gateway=new LarkSyncGateway(mapping,tokens,properties(),new LarkFailurePolicy(1),http);
    UUID token=UUID.randomUUID();String url="https://lark.invalid/open-apis/bitable/v1/apps/sandbox-fixture/tables/table-loads/records?client_token="+token+"&ignore_consistency_check=false";
    server.expect(requestTo(url)).andExpect(method(HttpMethod.POST))
      .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After","0"));
    server.expect(requestTo(url)).andExpect(content().json("{\"fields\":{\"TMS ID\":\"id-fixture\"}}"))
      .andRespond(withSuccess("{\"code\":0,\"data\":{\"record\":{\"record_id\":\"rec-fixture\",\"fields\":{}}}}",MediaType.APPLICATION_JSON));
    assertEquals("rec-fixture",gateway.retry(()->gateway.create("load",Map.of("TMS ID","id-fixture"),token)).recordId());
    server.verify();
  }
  @Test void placeholdersFailBeforeTokenLookupOrHttpRequest() {
    var env=LarkSyncMappingTest.environment();env.setProperty("LARK_BASE_APP_TOKEN","xxxxx");
    var tokens=mock(LarkBaseTokenProvider.class);var http=new RestTemplate();
    var server=MockRestServiceServer.bindTo(http).build();
    var gateway=new LarkSyncGateway(new LarkSyncMapping(env),tokens,properties(),new LarkFailurePolicy(1),http);
    assertThrows(com.company.logicstic.exception.ApiException.class,()->gateway.get("load","rec-fixture"));
    verifyNoInteractions(tokens);server.verify();
  }
  @Test void missingRecordCodeIsNotRetried() {
    var tokens=mock(LarkBaseTokenProvider.class);when(tokens.getTenantAccessToken()).thenReturn("fixture-token");
    var http=new RestTemplate();var server=MockRestServiceServer.bindTo(http).build();
    var gateway=new LarkSyncGateway(new LarkSyncMapping(LarkSyncMappingTest.environment()),tokens,properties(),new LarkFailurePolicy(1),http);
    server.expect(anything()).andRespond(withSuccess("{\"code\":1254043}",MediaType.APPLICATION_JSON));
    var error=assertThrows(com.company.logicstic.exception.ApiException.class,()->gateway.retry(()->gateway.get("load","rec-fixture")));
    assertEquals("LARK_BASE_RECORD_MISSING",error.getCode());server.verify();
  }
}

