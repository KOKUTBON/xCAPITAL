package com.alphamath.portfolio.application.broker;

import com.alphamath.portfolio.application.execution.ExecutionApprovalGuard;
import com.alphamath.portfolio.domain.broker.BrokerOrderRequest;
import com.alphamath.portfolio.domain.execution.ExecutionStatus;
import com.alphamath.portfolio.infrastructure.persistence.*;
import com.alphamath.portfolio.security.TenantContext;
import org.junit.jupiter.api.*;
import org.slf4j.MDC;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BrokerExecutionBoundaryTest {
  final BrokerAdapter adapter = mock(BrokerAdapter.class);
  final BrokerAccountRepository accounts = mock(BrokerAccountRepository.class);
  final ExecutionIntentRepository intents = mock(ExecutionIntentRepository.class);
  final ExecutionApprovalGuard approval = mock(ExecutionApprovalGuard.class);
  final BrokerIntegrationService service = new BrokerIntegrationService(null, List.of(adapter), null, accounts,
      null, null, null, null, null, null, null, null, null, new TenantContext(), approval, intents, 50);
  BrokerAccountEntity account;
  @BeforeEach void setup() {
    MDC.put("orgId", "org-a"); account = new BrokerAccountEntity(); account.setId("account");
    account.setUserId("user"); account.setOrgId("org-a"); when(accounts.findById("account")).thenReturn(Optional.of(account));
  }
  @AfterEach void cleanup() { MDC.clear(); }
  void status(int expected, Runnable action) {
    assertEquals(expected, assertThrows(ResponseStatusException.class, action::run).getStatusCode().value());
  }
  @Test void arbitraryOrderRouteCannotReachAdapter() {
    status(409, () -> service.placeOrder("user", "account", new BrokerOrderRequest())); verifyNoInteractions(adapter);
  }
  @Test void everyAccountOrderRouteRejectsWrongOrMissingTenant() {
    for (String org : List.of("org-b", "")) {
      if (org.isEmpty()) MDC.clear(); else MDC.put("orgId", org);
      status(404, () -> service.placeOrder("user", "account", new BrokerOrderRequest()));
      status(404, () -> service.previewOrder("user", "account", new BrokerOrderRequest()));
      status(404, () -> service.cancelOrder("user", "account", "order"));
      status(404, () -> service.refreshOrder("user", "account", "order"));
      status(404, () -> service.listOrders("user", "account"));
    }
    verifyNoInteractions(adapter);
  }
  @Test void internalPlacementRequiresAnAlreadyClaimedIntent() {
    var intent = new ExecutionIntentEntity(); intent.setBrokerAccountId("account"); intent.setStatus(ExecutionStatus.AUTHORIZED);
    when(intents.findById("intent")).thenReturn(Optional.of(intent));
    when(approval.requireBoundIntent("user", intent)).thenReturn(new TradeProposalEntity());
    status(409, () -> service.placeApprovedOrder("user", "account", "intent", 0)); verifyNoInteractions(adapter);
  }
}
