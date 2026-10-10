package com.alphamath.portfolio.application.audit;

import com.alphamath.portfolio.domain.audit.AuditEvent;
import com.alphamath.portfolio.infrastructure.persistence.AuditEventRepository;
import com.alphamath.portfolio.security.TenantContext;
import org.slf4j.MDC;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceOrgScopeTest {

  @Mock
  private AuditEventRepository events;

  private TenantContext tenantContext;
  private AuditService service;

  @BeforeEach
  void setUp() {
    tenantContext = new TenantContext();
    service = new AuditService(events, tenantContext);
  }

  @AfterEach
  void tearDown() {
    MDC.clear();
  }

  @Test
  void listUsesOrgScopedEventQueryWhenOrgContextExists() {
    MDC.put("orgId", "org-1");
    when(events.findByUserIdAndOrgIdAndEventTypeOrderByCreatedAtDesc(
        eq("user-1"), eq("org-1"), eq("PROPOSAL_CREATED"), any(Pageable.class)))
        .thenReturn(List.of());

    List<AuditEvent> result = service.list("user-1", "PROPOSAL_CREATED", null, 25);

    assertTrue(result.isEmpty());
    verify(events).findByUserIdAndOrgIdAndEventTypeOrderByCreatedAtDesc(
        eq("user-1"), eq("org-1"), eq("PROPOSAL_CREATED"), any(Pageable.class));
    verify(events, never()).findByUserIdAndEventTypeOrderByCreatedAtDesc(
        eq("user-1"), eq("PROPOSAL_CREATED"), any(Pageable.class));
  }

  @Test
  void listUsesUserScopedEntityQueryWhenOrgContextMissing() {
    MDC.remove("orgId");
    when(events.findByUserIdAndEntityIdOrderByCreatedAtDesc(
        eq("user-1"), eq("proposal-1"), any(Pageable.class)))
        .thenReturn(List.of());

    List<AuditEvent> result = service.list("user-1", null, "proposal-1", 25);

    assertTrue(result.isEmpty());
    verify(events).findByUserIdAndEntityIdOrderByCreatedAtDesc(
        eq("user-1"), eq("proposal-1"), any(Pageable.class));
  }

  @Test
  void exportCsvUsesOrgScopedListQueryWhenOrgContextExists() {
    MDC.put("orgId", "org-1");
    when(events.findByUserIdAndOrgIdOrderByCreatedAtDesc(
        eq("user-1"), eq("org-1"), any(Pageable.class)))
        .thenReturn(List.of());

    String csv = service.exportCsv("user-1", null, null, 20);

    assertTrue(csv.startsWith("id,orgId,actor,eventType,entityType,entityId,createdAt,prevHash,eventHash,metadata"));
    verify(events).findByUserIdAndOrgIdOrderByCreatedAtDesc(eq("user-1"), eq("org-1"), any(Pageable.class));
    verify(events, never()).findByUserIdOrderByCreatedAtDesc(eq("user-1"), any(Pageable.class));
  }
}
