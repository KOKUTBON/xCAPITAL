package com.alphamath.portfolio.security;

import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import java.util.Objects;

@Component
public class TenantContext {
  public String getOrgId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null) {
      return authentication.getDetails() instanceof AuthenticatedRequestContext context ? context.orgId() : null;
    }
    String value = MDC.get("orgId");
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }

  /** A missing organization never grants access to organization-owned records. */
  public boolean owns(String userId, String recordUserId, String recordOrgId) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && !Objects.equals(userId, authentication.getName())) return false;
    return userId != null && Objects.equals(userId, recordUserId)
        && Objects.equals(getOrgId(), recordOrgId);
  }

}
