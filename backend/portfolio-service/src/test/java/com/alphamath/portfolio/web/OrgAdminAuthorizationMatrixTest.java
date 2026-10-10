package com.alphamath.portfolio.web;

import com.alphamath.portfolio.application.reporting.OrgAdminReportingService;
import com.alphamath.portfolio.security.AuthenticatedRequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrgAdminAuthorizationMatrixTest {

  private StubOrgAdminReportingService reporting;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    reporting = new StubOrgAdminReportingService();
    SecurityPolicyProperties properties = new SecurityPolicyProperties();
    properties.getRbac().setEnforce(true);
    SecurityGuard security = new SecurityGuard(properties);
    mvc = MockMvcBuilders.standaloneSetup(new OrgAdminController(reporting, security)).build();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void summaryAllowsOwnerRole() throws Exception {
    authenticate("org-1", "OWNER");
    reporting.summaryResponse = Map.of("orgId", "org-1", "counts", Map.of());

    mvc.perform(get("/api/v1/admin/org/summary"))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"orgId\":\"org-1\"}", false));

    assertEquals(1, reporting.summaryCalls);
  }

  @Test
  void auditEventsAllowsAdminRole() throws Exception {
    authenticate("org-1", "ADMIN");
    reporting.auditResponse = List.of(Map.of("id", "evt-1"));

    mvc.perform(get("/api/v1/admin/org/audit/events").param("limit", "10"))
        .andExpect(status().isOk())
        .andExpect(content().json("[{\"id\":\"evt-1\"}]"));

    assertEquals(1, reporting.auditCalls);
    assertEquals(10, reporting.lastAuditLimit);
  }

  @Test
  void summaryRejectsMemberRole() throws Exception {
    authenticate("org-1", "MEMBER");

    mvc.perform(get("/api/v1/admin/org/summary"))
        .andExpect(status().isForbidden());

    assertEquals(0, reporting.summaryCalls);
    assertEquals(0, reporting.auditCalls);
  }

  @Test
  void summaryRejectsWhenNoTrustedOrgRoleContextExists() throws Exception {
    SecurityContextHolder.clearContext();

    mvc.perform(get("/api/v1/admin/org/summary").header("X-Org-Roles", "OWNER"))
        .andExpect(status().isForbidden());

    assertEquals(0, reporting.summaryCalls);
    assertEquals(0, reporting.auditCalls);
  }

  @Test
  void summaryRejectsSpoofedHeaderWhenTokenOrgRoleIsMember() throws Exception {
    authenticate("org-1", "MEMBER");

    mvc.perform(get("/api/v1/admin/org/summary").header("X-Org-Roles", "OWNER"))
        .andExpect(status().isForbidden());

    assertEquals(0, reporting.summaryCalls);
    assertEquals(0, reporting.auditCalls);
  }

  private void authenticate(String orgId, String... orgRoles) {
    Set<String> normalizedOrgRoles = Arrays.stream(orgRoles)
        .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
    AuthenticatedRequestContext context = new AuthenticatedRequestContext(
        "user-1",
        "user-1@xcapital.test",
        orgId,
        Set.of("USER"),
        normalizedOrgRoles
    );
    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken("user-1", null, Collections.emptyList());
    authentication.setDetails(context);
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  private static class StubOrgAdminReportingService extends OrgAdminReportingService {
    private int summaryCalls;
    private int auditCalls;
    private int lastAuditLimit;
    private Map<String, Object> summaryResponse = Map.of();
    private List<Map<String, Object>> auditResponse = List.of();

    StubOrgAdminReportingService() {
      super(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    @Override
    public Map<String, Object> summary() {
      summaryCalls++;
      return summaryResponse;
    }

    @Override
    public List<Map<String, Object>> recentAudit(int limit) {
      auditCalls++;
      lastAuditLimit = limit;
      return auditResponse;
    }
  }
}
