package com.company.logicstic.config;

import jakarta.servlet.DispatcherType;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.*;

/** Prevents injected correlation headers and context leaking between servlet threads. */
class RequestIdFilterTests {
    @AfterEach void clear() { MDC.clear(); }
    @Test void validatesOrGeneratesIdsAndKeepsBodyHeaderAndMdcTogether() throws Exception {
        var filter=new RequestIdFilter();
        for (String input : List.of("accepted:request-1", "", "bad\r\nid", "with space", "a".repeat(121))) {
            var request=new MockHttpServletRequest("GET","/api/test");
            request.addHeader(RequestIds.HEADER,input);
            var response=new MockHttpServletResponse();
            filter.doFilter(request,response,(req,res)-> {
                String id=RequestIds.getOrCreate(request);
                assertThat(id).matches("[A-Za-z0-9._:-]{1,120}");
                assertThat(MDC.get("requestId")).isEqualTo(id);
                assertThat(com.company.logicstic.dto.ResponseMeta.from(request).requestId()).isEqualTo(id);
                assertThat(response.getHeader(RequestIds.HEADER)).isEqualTo(id);
                if (input.equals("accepted:request-1")) assertThat(id).isEqualTo(input);
                else assertThat(id).isNotEqualTo(input);
            });
            assertThat(MDC.get("requestId")).isNull();
        }
    }
    @Test void errorAndAsyncDispatchReuseIdRestoreMdcAndPreserveTrace() throws Exception {
        var request=new MockHttpServletRequest();var filter=new RequestIdFilter();
        filter.doFilter(request,new MockHttpServletResponse(),(req,res)->{});
        String id=RequestIds.getOrCreate(request);
        MDC.put("requestId","outer");MDC.put("traceId","existing-trace");
        for(var dispatch: List.of(DispatcherType.ERROR,DispatcherType.ASYNC)) {
            request.setDispatcherType(dispatch);var response=new MockHttpServletResponse();
            filter.doFilter(request,response,(req,res)-> {
                assertThat(MDC.get("requestId")).isEqualTo(id);
                assertThat(MDC.get("traceId")).isEqualTo("existing-trace");
            });
            assertThat(MDC.get("requestId")).isEqualTo("outer");
            assertThat(response.getHeader(RequestIds.HEADER)).isEqualTo(id);
        }
    }
    @Test void exceptionAndEarlyRejectionAlwaysCleanMdc() {
        var request=new MockHttpServletRequest();var response=new MockHttpServletResponse();
        assertThatThrownBy(()->new RequestIdFilter().doFilter(request,response,(req,res)->{throw new IllegalStateException("fixture");}))
                .isInstanceOf(IllegalStateException.class);
        assertThat(MDC.get("requestId")).isNull();
    }
}
