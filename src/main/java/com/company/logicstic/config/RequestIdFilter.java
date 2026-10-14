package com.company.logicstic.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Runs before security so rejected, error and async dispatches carry the same ID. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {
    @Override protected boolean shouldNotFilterAsyncDispatch() { return false; }
    @Override protected boolean shouldNotFilterErrorDispatch() { return false; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String previous = MDC.get("requestId");
        String id = RequestIds.getOrCreate(request);
        response.setHeader(RequestIds.HEADER, id);
        MDC.put("requestId", id);
        try { chain.doFilter(request, response); }
        finally {
            if (previous == null) MDC.remove("requestId"); else MDC.put("requestId", previous);
        }
    }
    @Override protected void doFilterNestedErrorDispatch(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException { doFilterInternal(request, response, chain); }
}
