package com.alphamath.portfolio.application.execution;

import com.alphamath.portfolio.application.audit.AuditService;
import com.alphamath.portfolio.application.broker.BrokerIntegrationService;
import com.alphamath.portfolio.application.compliance.ComplianceService;
import com.alphamath.portfolio.domain.broker.*;
import com.alphamath.portfolio.domain.execution.*;
import com.alphamath.portfolio.domain.trade.*;
import com.alphamath.portfolio.infrastructure.persistence.*;
import com.alphamath.portfolio.security.TenantContext;
import org.junit.jupiter.api.*;
import org.slf4j.MDC;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExecutionApprovalBoundaryTest {
  final TradeProposalRepository proposals = mock(TradeProposalRepository.class);
  final TradeOrderRepository orders = mock(TradeOrderRepository.class);
  final ExecutionIntentRepository intents = mock(ExecutionIntentRepository.class);
  final BrokerAccountRepository accounts = mock(BrokerAccountRepository.class);
  final ExecutionFillRepository fills = mock(ExecutionFillRepository.class);
  final BrokerIntegrationService brokers = mock(BrokerIntegrationService.class);
  final ComplianceService compliance = mock(ComplianceService.class);
  final TenantContext tenant = new TenantContext();
  final ExecutionApprovalGuard guard = new ExecutionApprovalGuard(proposals, orders, tenant);
  final ExecutionService service = new ExecutionService(accounts, intents, fills, brokers,
      mock(AuditService.class), tenant, guard, proposals, compliance);
  TradeProposalEntity proposal;
  TradeOrderEntity order;
  ExecutionIntentEntity intent;

  @BeforeEach void setup() {
    MDC.put("orgId", "org-a");
    proposal = new TradeProposalEntity();
    proposal.setId("proposal"); proposal.setUserId("user"); proposal.setOrgId("org-a");
    proposal.setAccountId("cash-account"); proposal.setStatus("APPROVED");
    proposal.setExecutionMode(ExecutionMode.LIVE); proposal.setRegion(Region.US);
    proposal.setAssetClass(AssetClass.EQUITY); proposal.setProviderPreference("alpaca");
    proposal.setOrderType(OrderType.LIMIT); proposal.setTimeInForce(TimeInForce.DAY);
    proposal.setApprovedBy("user"); proposal.setApprovedAt(Instant.now());
    order = new TradeOrderEntity(); order.setId("order"); order.setProposalId("proposal");
    order.setSymbol("AAPL"); order.setSide("BUY"); order.setQuantity(2.0); order.setPrice(100.0);
    order.setNotional(200.0); order.setFee(1.0);
    proposal.setApprovalHash(ExecutionApprovalGuard.hash(proposal, List.of(order)));
    when(proposals.findById("proposal")).thenReturn(Optional.of(proposal));
    when(proposals.findByIdForUpdate("proposal")).thenReturn(Optional.of(proposal));
    when(orders.findByProposalIdOrderByCreatedAtAsc("proposal")).thenReturn(List.of(order));
    intent = new ExecutionIntentEntity(); intent.setId("intent"); intent.setProposalId("proposal");
    intent.setUserId("user"); intent.setOrgId("org-a"); intent.setProviderId("alpaca");
    intent.setRegion(Region.US); intent.setAssetClass(AssetClass.EQUITY);
    intent.setBrokerAccountId("broker-account"); intent.setApprovalHash(proposal.getApprovalHash());
    intent.setStatus(ExecutionStatus.AUTHORIZED); intent.setCreatedAt(Instant.now());
    intent.setOrdersJson(JsonUtils.toJson(guard.executionOrders(proposal)));
    proposal.setExecutionIntentId("intent");
    when(intents.findById("intent")).thenReturn(Optional.of(intent));
  }
  @AfterEach void cleanup() { MDC.clear(); }
  void status(int expected, Runnable action) {
    assertEquals(expected, assertThrows(ResponseStatusException.class, action::run).getStatusCode().value());
  }
  @Test void rejectsEveryUnapprovedStateAndPaperOrders() {
    for (String state : List.of("PENDING_USER", "DECLINED", "EXECUTED")) {
      proposal.setStatus(state); status(409, () -> guard.requireApproved("user", "proposal"));
    }
    proposal.setStatus("APPROVED"); proposal.setExecutionMode(ExecutionMode.PAPER);
    status(409, () -> guard.requireApproved("user", "proposal"));
    verifyNoInteractions(brokers);
  }
  @Test void approvalRequiresActorAndTimestampEvidence() {
    proposal.setApprovedBy("another-user"); status(409, () -> service.submitIntent("user", "intent"));
    proposal.setApprovedBy("user"); proposal.setApprovedAt(null);
    status(409, () -> service.submitIntent("user", "intent")); verifyNoInteractions(brokers);
  }
  @Test void legacyStatusAloneCannotAuthorizeExecution() {
    proposal.setApprovalHash(null); status(409, () -> service.submitIntent("user", "intent"));
    verifyNoInteractions(brokers); verify(intents, never()).claimSubmission(any(), any(), any(), any(), any(), any());
  }
  @Test void alteredQuantityOrDestinationInvalidatesApproval() {
    order.setQuantity(3.0); status(409, () -> service.submitIntent("user", "intent"));
    order.setQuantity(2.0); proposal.setAccountId("different");
    status(409, () -> service.submitIntent("user", "intent")); verifyNoInteractions(brokers);
  }
  @Test void alteredIntentOrUnlinkedIntentIsRejected() {
    intent.setOrdersJson("[]"); status(409, () -> guard.requireBoundIntent("user", intent));
    intent.setOrdersJson(JsonUtils.toJson(guard.executionOrders(proposal)));
    proposal.setExecutionIntentId("different"); status(409, () -> service.submitIntent("user", "intent"));
    verifyNoInteractions(brokers);
  }
  @Test void wrongOrMissingTenantCannotReadCreateOrSubmit() {
    TradeProposal dto = new TradeProposal(); dto.setId("proposal");
    for (String org : List.of("org-b", "")) {
      if (org.isEmpty()) MDC.clear(); else MDC.put("orgId", org);
      status(404, () -> service.createIntent("user", dto));
      status(404, () -> service.submitIntent("user", "intent"));
      status(404, () -> guard.requireApproved("user", "proposal"));
    }
    verifyNoInteractions(brokers);
  }
  @Test void creationUsesPersistedOrdersAndReturnsSameIntentOnRepeat() {
    proposal.setExecutionIntentId(null);
    BrokerAccountEntity account = new BrokerAccountEntity(); account.setId("broker-account");
    account.setUserId("user"); account.setOrgId("org-a"); account.setProviderId("alpaca");
    account.setStatus(BrokerAccountStatus.LINKED); account.setRegion(Region.US);
    account.setAssetClassesJson("[\"EQUITY\"]");
    when(accounts.findByUserIdAndOrgIdOrderByCreatedAtDesc("user", "org-a")).thenReturn(List.of(account));
    when(intents.saveAndFlush(any())).thenAnswer(inv -> {
      ExecutionIntentEntity saved = inv.getArgument(0);
      when(intents.findById(saved.getId())).thenReturn(Optional.of(saved)); return saved;
    });
    TradeProposal forged = new TradeProposal(); forged.setId("proposal"); forged.setProviderPreference("different");
    forged.setOrders(List.of());
    ExecutionIntent created = service.createIntent("user", forged);
    assertEquals("alpaca", created.getProviderId()); assertEquals(2.0, created.getOrders().getFirst().getQuantity());
    assertEquals(ExecutionStatus.AUTHORIZED, created.getStatus());
    assertEquals(created.getId(), service.createIntent("user", forged).getId());
    verify(intents, times(1)).saveAndFlush(any());
  }
  @Test void lostConcurrentClaimMakesNoBrokerCall() {
    status(409, () -> service.submitIntent("user", "intent")); verifyNoInteractions(brokers);
  }
  void claim() {
    when(intents.claimSubmission("intent", "user", "org-a", proposal.getApprovalHash(),
        ExecutionStatus.AUTHORIZED, ExecutionStatus.SUBMITTING)).thenReturn(1);
  }
  @Test void acceptedSubmissionUsesBoundAccountAndCannotRepeat() {
    claim(); BrokerOrder placed = new BrokerOrder(); placed.setId("broker-order"); placed.setStatus(BrokerOrderStatus.SUBMITTED);
    when(brokers.placeApprovedOrder("user", "broker-account", "intent", 0)).thenReturn(placed);
    assertEquals(ExecutionStatus.SUBMITTED, service.submitIntent("user", "intent").getStatus());
    status(409, () -> service.submitIntent("user", "intent"));
    verify(brokers, times(1)).placeApprovedOrder("user", "broker-account", "intent", 0);
    verifyNoInteractions(fills);
  }
  @Test void providerUncertaintyRetainsClaimAndBlocksRetries() {
    claim(); when(brokers.placeApprovedOrder(any(), any(), any(), anyInt())).thenThrow(new IllegalStateException("timeout"));
    status(502, () -> service.submitIntent("user", "intent"));
    verify(intents).markSubmissionUnknown("intent", ExecutionStatus.SUBMITTING, ExecutionStatus.SUBMISSION_UNKNOWN);
    status(409, () -> service.submitIntent("user", "intent"));
    verify(brokers, times(1)).placeApprovedOrder(any(), any(), any(), anyInt());
  }
  @Test void syntheticFillCannotChangeExecutionOrBalances() {
    status(409, () -> service.simulateFill("user", "intent")); verifyNoInteractions(fills, brokers);
    verify(intents, never()).save(any());
  }
}
