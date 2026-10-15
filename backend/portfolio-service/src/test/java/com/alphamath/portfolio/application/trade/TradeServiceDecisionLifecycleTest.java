package com.alphamath.portfolio.application.trade;

import com.alphamath.portfolio.application.audit.AuditService;
import com.alphamath.portfolio.application.compliance.ComplianceService;
import com.alphamath.portfolio.application.execution.BestExecutionService;
import com.alphamath.portfolio.application.execution.ExecutionService;
import com.alphamath.portfolio.application.policy.ProviderPolicyService;
import com.alphamath.portfolio.application.surveillance.SurveillanceService;
import com.alphamath.portfolio.domain.execution.AssetClass;
import com.alphamath.portfolio.domain.execution.ExecutionIntent;
import com.alphamath.portfolio.domain.execution.OrderType;
import com.alphamath.portfolio.domain.execution.Region;
import com.alphamath.portfolio.domain.execution.TimeInForce;
import com.alphamath.portfolio.domain.trade.DecisionAction;
import com.alphamath.portfolio.domain.trade.ExecutionMode;
import com.alphamath.portfolio.domain.trade.TradeDecisionRequest;
import com.alphamath.portfolio.domain.trade.TradeProposal;
import com.alphamath.portfolio.domain.trade.TradeStatus;
import com.alphamath.portfolio.infrastructure.ai.AiForecastService;
import com.alphamath.portfolio.infrastructure.persistence.PortfolioAccountRepository;
import com.alphamath.portfolio.infrastructure.persistence.PortfolioPositionRepository;
import com.alphamath.portfolio.infrastructure.persistence.TradeOrderEntity;
import com.alphamath.portfolio.infrastructure.persistence.TradeOrderRepository;
import com.alphamath.portfolio.infrastructure.persistence.TradeProposalEntity;
import com.alphamath.portfolio.infrastructure.persistence.TradeProposalRepository;
import com.alphamath.portfolio.security.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.slf4j.MDC;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TradeServiceDecisionLifecycleTest {
  @AfterEach
  void clearContext() { MDC.clear(); }

  @Mock
  private ComplianceService compliance;
  @Mock
  private ExecutionService execution;
  @Mock
  private AiForecastService aiForecast;
  @Mock
  private ProviderPolicyService providerPolicy;
  @Mock
  private SurveillanceService surveillance;
  @Mock
  private BestExecutionService bestExecution;
  @Mock
  private PortfolioAccountRepository accounts;
  @Mock
  private PortfolioPositionRepository positions;
  @Mock
  private TradeProposalRepository proposals;
  @Mock
  private TradeOrderRepository orders;
  @Mock
  private AuditService audit;

  private TradeService service;

  @BeforeEach
  void setUp() {
    TenantContext tenantContext = new TenantContext();
    service = new TradeService(
        compliance,
        execution,
        aiForecast,
        providerPolicy,
        surveillance,
        bestExecution,
        accounts,
        positions,
        proposals,
        orders,
        audit,
        tenantContext,
        50.0
    );
  }

  @Test
  void paperApprovalCannotMutateAccountInAnotherTenant() {
    MDC.put("orgId", "org-a");
    TradeProposalEntity entity = pendingProposalEntity(ExecutionMode.PAPER);
    entity.setOrgId("org-a"); entity.setAccountId("account-other");
    var account = new com.alphamath.portfolio.infrastructure.persistence.PortfolioAccountEntity();
    account.setId("account-other"); account.setUserId("user-1"); account.setOrgId("org-b");
    when(proposals.findByIdForUpdate("proposal-1")).thenReturn(Optional.of(entity));
    when(orders.findByProposalIdOrderByCreatedAtAsc("proposal-1")).thenReturn(List.of(orderEntity("proposal-1")));
    when(accounts.findById("account-other")).thenReturn(Optional.of(account));
    assertEquals(404, assertThrows(ResponseStatusException.class,
        () -> service.decide("user-1", "proposal-1", decision(DecisionAction.APPROVE))).getStatusCode().value());
    verifyNoInteractions(positions); verify(accounts, never()).save(any());
  }

  @Test
  void decideWaitKeepsProposalPendingWithoutPersistenceOrAudit() {
    TradeProposalEntity entity = pendingProposalEntity(ExecutionMode.LIVE);
    when(proposals.findByIdForUpdate("proposal-1")).thenReturn(Optional.of(entity));
    when(orders.findByProposalIdOrderByCreatedAtAsc("proposal-1")).thenReturn(List.of(orderEntity("proposal-1")));

    TradeProposal result = service.decide("user-1", "proposal-1", decision(DecisionAction.WAIT));

    assertEquals(TradeStatus.PENDING_USER, result.getStatus());
    verify(proposals, never()).save(any(TradeProposalEntity.class));
    verify(audit, never()).record(any(), any(), any(), any(), any(), anyMap());
  }

  @Test
  void decideDeclinePersistsDeclinedStatusAndWritesAudit() {
    TradeProposalEntity entity = pendingProposalEntity(ExecutionMode.LIVE);
    when(proposals.findByIdForUpdate("proposal-1")).thenReturn(Optional.of(entity));
    when(orders.findByProposalIdOrderByCreatedAtAsc("proposal-1")).thenReturn(List.of(orderEntity("proposal-1")));

    TradeProposal result = service.decide("user-1", "proposal-1", decision(DecisionAction.DECLINE));

    assertEquals(TradeStatus.DECLINED, result.getStatus());
    verify(proposals).save(any(TradeProposalEntity.class));
    verify(audit).record(
        eq("user-1"),
        eq("user-1"),
        eq("PROPOSAL_DECLINED"),
        eq("portfolio_trade_proposal"),
        eq("proposal-1"),
        anyMap()
    );
  }

  @Test
  void decideApproveLiveFailsWhenBrokerAccountIsNotLinked() {
    TradeProposalEntity entity = pendingProposalEntity(ExecutionMode.LIVE);
    when(proposals.findByIdForUpdate("proposal-1")).thenReturn(Optional.of(entity));
    when(orders.findByProposalIdOrderByCreatedAtAsc("proposal-1")).thenReturn(List.of(orderEntity("proposal-1")));
    when(execution.hasLinkedAccount("user-1", Region.US, AssetClass.EQUITY, "interactive_brokers"))
        .thenReturn(false);

    ResponseStatusException ex = assertThrows(ResponseStatusException.class,
        () -> service.decide("user-1", "proposal-1", decision(DecisionAction.APPROVE)));

    assertEquals(412, ex.getStatusCode().value());
    verify(compliance).requireLiveEligibility("user-1");
    verify(execution, never()).createIntent(eq("user-1"), any(TradeProposal.class));
    verify(proposals, never()).save(any(TradeProposalEntity.class));
  }

  @Test
  void decideApproveLiveCreatesExecutionIntentAndMarksSubmitted() {
    TradeProposalEntity entity = pendingProposalEntity(ExecutionMode.LIVE);
    when(proposals.findByIdForUpdate("proposal-1")).thenReturn(Optional.of(entity));
    when(orders.findByProposalIdOrderByCreatedAtAsc("proposal-1")).thenReturn(List.of(orderEntity("proposal-1")));
    when(execution.hasLinkedAccount("user-1", Region.US, AssetClass.EQUITY, "interactive_brokers"))
        .thenReturn(true);
    ExecutionIntent intent = new ExecutionIntent();
    intent.setId("intent-1");
    when(execution.createIntent(eq("user-1"), any(TradeProposal.class))).thenReturn(intent);

    TradeProposal result = service.decide("user-1", "proposal-1", decision(DecisionAction.APPROVE));

    assertEquals(TradeStatus.SUBMITTED, result.getStatus());
    assertEquals("intent-1", result.getExecutionIntentId());
    verify(compliance).requireLiveEligibility("user-1");
    verify(execution).createIntent(eq("user-1"), any(TradeProposal.class));
    ArgumentCaptor<TradeProposalEntity> approved = ArgumentCaptor.forClass(TradeProposalEntity.class);
    verify(proposals).saveAndFlush(approved.capture());
    assertEquals(TradeStatus.APPROVED.name(), approved.getValue().getStatus());
    assertEquals("user-1", approved.getValue().getApprovedBy());
    org.junit.jupiter.api.Assertions.assertNotNull(approved.getValue().getApprovedAt());
    org.junit.jupiter.api.Assertions.assertNotNull(approved.getValue().getApprovalHash());
    verify(proposals).save(any(TradeProposalEntity.class));
    verify(audit).record(
        eq("user-1"),
        eq("user-1"),
        eq("PROPOSAL_APPROVED"),
        eq("portfolio_trade_proposal"),
        eq("proposal-1"),
        anyMap()
    );
  }

  @Test
  void decisionsRejectSameUserInAnotherOrganizationBeforeLoadingOrders() {
    MDC.put("orgId", "org-a");
    TradeProposalEntity entity = pendingProposalEntity(ExecutionMode.LIVE);
    entity.setOrgId("org-b");
    when(proposals.findByIdForUpdate("proposal-1")).thenReturn(Optional.of(entity));
    for (DecisionAction action : DecisionAction.values()) {
      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
          () -> service.decide("user-1", "proposal-1", decision(action)));
      assertEquals(404, ex.getStatusCode().value());
    }
    org.mockito.Mockito.verifyNoInteractions(orders, execution, compliance, audit);
  }

  @Test
  void decisionWithoutOrgCannotMutateTenantProposal() {
    TradeProposalEntity entity = pendingProposalEntity(ExecutionMode.LIVE);
    entity.setOrgId("org-b");
    when(proposals.findByIdForUpdate("proposal-1")).thenReturn(Optional.of(entity));
    assertEquals(404, assertThrows(ResponseStatusException.class,
        () -> service.decide("user-1", "proposal-1", decision(DecisionAction.APPROVE))).getStatusCode().value());
    org.mockito.Mockito.verifyNoInteractions(orders, execution, compliance, audit);
  }

  @Test
  void decideRejectsNonPendingProposalStates() {
    TradeProposalEntity entity = pendingProposalEntity(ExecutionMode.LIVE);
    entity.setStatus(TradeStatus.EXECUTED.name());
    when(proposals.findByIdForUpdate("proposal-1")).thenReturn(Optional.of(entity));
    when(orders.findByProposalIdOrderByCreatedAtAsc("proposal-1")).thenReturn(List.of(orderEntity("proposal-1")));

    ResponseStatusException ex = assertThrows(ResponseStatusException.class,
        () -> service.decide("user-1", "proposal-1", decision(DecisionAction.APPROVE)));

    assertEquals(409, ex.getStatusCode().value());
    verify(proposals, never()).save(any(TradeProposalEntity.class));
    verify(compliance, never()).requireLiveEligibility(any());
    verify(execution, never()).createIntent(eq("user-1"), any(TradeProposal.class));
  }

  private TradeDecisionRequest decision(DecisionAction action) {
    TradeDecisionRequest req = new TradeDecisionRequest();
    req.setAction(action);
    return req;
  }

  private TradeProposalEntity pendingProposalEntity(ExecutionMode mode) {
    TradeProposalEntity entity = new TradeProposalEntity();
    entity.setId("proposal-1");
    entity.setUserId("user-1");
    entity.setAccountId("acct-1");
    entity.setStatus(TradeStatus.PENDING_USER.name());
    entity.setCreatedAt(Instant.parse("2026-03-13T09:00:00Z"));
    entity.setExecutionMode(mode);
    entity.setRegion(Region.US);
    entity.setAssetClass(AssetClass.EQUITY);
    entity.setProviderPreference("interactive_brokers");
    entity.setOrderType(OrderType.MARKET);
    entity.setTimeInForce(TimeInForce.DAY);
    entity.setSymbolsJson("[\"AAPL\"]");
    entity.setPricesJson("{\"AAPL\":190.0}");
    entity.setCurrentWeightsJson("{\"AAPL\":1.0}");
    entity.setTargetWeightsJson("{\"AAPL\":1.0}");
    entity.setPolicyChecksJson("[]");
    entity.setPayload("{}");
    return entity;
  }

  private TradeOrderEntity orderEntity(String proposalId) {
    TradeOrderEntity order = new TradeOrderEntity();
    order.setId("order-1");
    order.setProposalId(proposalId);
    order.setSymbol("AAPL");
    order.setSide("BUY");
    order.setQuantity(1.0);
    order.setPrice(190.0);
    order.setNotional(190.0);
    order.setFee(0.0);
    order.setCreatedAt(Instant.parse("2026-03-13T09:00:00Z"));
    return order;
  }
}
