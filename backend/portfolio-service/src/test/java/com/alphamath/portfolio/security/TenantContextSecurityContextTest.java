package com.alphamath.portfolio.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TenantContextSecurityContextTest {

  private final TenantContext tenantContext = new TenantContext();

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    MDC.clear();
  }

  @Test
  void getOrgIdPrefersTrustedAuthenticatedContext() {
    MDC.put("orgId", "spoofed-org");
    authenticate("trusted-org", "OWNER");

    assertEquals("trusted-org", tenantContext.getOrgId());
  }

  @Test
  void getOrgIdFallsBackToMdcWhenAuthenticationContextMissing() {
    SecurityContextHolder.clearContext();
    MDC.put("orgId", "mdc-org");

    assertEquals("mdc-org", tenantContext.getOrgId());
  }

  @Test
  void getOrgIdReturnsNullWhenNoContextExists() {
    SecurityContextHolder.clearContext();
    MDC.clear();

    assertNull(tenantContext.getOrgId());
  }

  @Test
  void missingAuthenticatedOrganizationCannotFallBackToMdc() {
    MDC.put("orgId", "spoofed-org");
    authenticate(null, "OWNER");
    assertNull(tenantContext.getOrgId());
    org.junit.jupiter.api.Assertions.assertFalse(tenantContext.owns("user-1", "user-1", "spoofed-org"));
  }

  @Test
  void authenticatedPrincipalCannotBeReplacedByServiceArgument() {
    authenticate("org-a", "OWNER");
    org.junit.jupiter.api.Assertions.assertFalse(tenantContext.owns("other-user", "other-user", "org-a"));
  }

  private void authenticate(String orgId, String orgRole) {
    AuthenticatedRequestContext context = new AuthenticatedRequestContext(
        "user-1",
        "user-1@xcapital.test",
        orgId,
        Set.of("USER"),
        Set.of(orgRole)
    );
    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken("user-1", null, Collections.emptyList());
    authentication.setDetails(context);
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }
}
