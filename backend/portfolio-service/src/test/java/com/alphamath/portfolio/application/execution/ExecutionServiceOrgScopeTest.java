package com.alphamath.portfolio.application.execution;

import com.alphamath.portfolio.application.audit.AuditService;
import com.alphamath.portfolio.application.broker.BrokerIntegrationService;
import com.alphamath.portfolio.domain.execution.BrokerAccount;
import com.alphamath.portfolio.infrastructure.persistence.BrokerAccountRepository;
import com.alphamath.portfolio.infrastructure.persistence.ExecutionFillRepository;
import com.alphamath.portfolio.infrastructure.persistence.ExecutionIntentEntity;
import com.alphamath.portfolio.infrastructure.persistence.ExecutionIntentRepository;
import com.alphamath.portfolio.security.TenantContext;
import org.slf4j.MDC;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExecutionServiceOrgScopeTest {

  @Mock
  private BrokerAccountRepository accounts;
  @Mock
  private ExecutionIntentRepository intents;
  @Mock
  private ExecutionFillRepository fills;

  private TenantContext tenantContext;
  private ExecutionService service;

  @BeforeEach
  void setUp() {
    tenantContext = new TenantContext();
    service = new ExecutionService(accounts, intents, fills, null, null, tenantContext, null, null, null);
  }

  @AfterEach
  void tearDown() {
    MDC.clear();
  }

  @Test
  void listAccountsUsesOrgScopedRepositoryWhenOrgContextExists() {
    MDC.put("orgId", "org-1");
    when(accounts.findByUserIdAndOrgIdOrderByCreatedAtDesc("user-1", "org-1"))
        .thenReturn(List.of());

    List<BrokerAccount> result = service.listAccounts("user-1");

    assertTrue(result.isEmpty());
    verify(accounts).findByUserIdAndOrgIdOrderByCreatedAtDesc("user-1", "org-1");
    verify(accounts, never()).findByUserIdOrderByCreatedAtDesc("user-1");
  }

  @Test
  void getIntentRejectsOrgMismatches() {
    MDC.put("orgId", "org-1");
    ExecutionIntentEntity entity = new ExecutionIntentEntity();
    entity.setId("intent-1");
    entity.setUserId("user-1");
    entity.setOrgId("org-2");
    when(intents.findById("intent-1")).thenReturn(Optional.of(entity));

    ResponseStatusException ex = assertThrows(ResponseStatusException.class,
        () -> service.getIntent("user-1", "intent-1"));

    assertEquals(404, ex.getStatusCode().value());
  }

  @Test
  void allIntentEntryPointsRejectDifferentOrMissingOrganization() {
    ExecutionIntentEntity entity = new ExecutionIntentEntity();
    entity.setId("intent-1");
    entity.setUserId("user-1");
    entity.setOrgId("org-other");
    when(intents.findById("intent-1")).thenReturn(Optional.of(entity));
    for (String org : List.of("org-1", "")) {
      MDC.put("orgId", org);
      assertEquals(404, assertThrows(ResponseStatusException.class,
          () -> service.getIntent("user-1", "intent-1")).getStatusCode().value());
      assertEquals(404, assertThrows(ResponseStatusException.class,
          () -> service.submitIntent("user-1", "intent-1")).getStatusCode().value());
      assertEquals(404, assertThrows(ResponseStatusException.class,
          () -> service.simulateFill("user-1", "intent-1")).getStatusCode().value());
    }
    org.mockito.Mockito.verifyNoInteractions(fills);
    verify(intents, never()).save(org.mockito.ArgumentMatchers.any());
  }
}
