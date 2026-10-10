package com.alphamath.simulation.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class RequestTraceFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String requestId = headerOrGenerate(request, "X-Request-Id");
    String traceId = headerOrDefault(request, "X-Trace-Id", requestId);
    String userId = headerOrDefault(request, "X-User-Id", null);
    String orgId = headerOrDefault(request, "X-Org-Id", null);

    MDC.put("requestId", requestId);
    MDC.put("traceId", traceId);
    if (userId != null && !userId.isBlank()) {
      MDC.put("userId", userId);
    }
    if (orgId != null && !orgId.isBlank()) {
      MDC.put("orgId", orgId);
    }

    response.setHeader("X-Request-Id", requestId);
    response.setHeader("X-Trace-Id", traceId);
    response.addHeader("Access-Control-Expose-Headers", "X-Request-Id");
    response.addHeader("Access-Control-Expose-Headers", "X-Trace-Id");

    try {
      filterChain.doFilter(request, response);
    } finally {
      MDC.remove("requestId");
      MDC.remove("traceId");
      MDC.remove("userId");
      MDC.remove("orgId");
    }
  }

  private String headerOrGenerate(HttpServletRequest request, String header) {
    String value = request.getHeader(header);
    if (value == null || value.isBlank()) {
      return UUID.randomUUID().toString();
    }
    return value.trim();
  }

  private String headerOrDefault(HttpServletRequest request, String header, String fallback) {
    String value = request.getHeader(header);
    if (value == null || value.isBlank()) {
      return fallback;
    }
    return value.trim();
  }
}
