package com.alphamath.portfolio.application.funding;

import com.alphamath.portfolio.application.audit.AuditService;
import com.alphamath.portfolio.application.trade.TradeService;
import com.alphamath.portfolio.domain.funding.*;
import com.alphamath.portfolio.infrastructure.persistence.*;
import com.alphamath.portfolio.security.TenantContext;
import org.junit.jupiter.api.*;
import org.slf4j.MDC;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FundingExecutionBoundaryTest {
  final FundingSourceRepository sources = mock(FundingSourceRepository.class);
  final BrokerAccountRepository accounts = mock(BrokerAccountRepository.class);
  final FundingAdapter live = mock(FundingAdapter.class);
  final TradeService trade = mock(TradeService.class);
  final FundingDepositRepository deposits = mock(FundingDepositRepository.class);
  final FundingWithdrawalRepository withdrawals = mock(FundingWithdrawalRepository.class);
  final FundingTransferRepository transfers = mock(FundingTransferRepository.class);
  final FundingService service = new FundingService(trade, sources, deposits, withdrawals, transfers,
      accounts, List.of(live), mock(AuditService.class), new TenantContext());
  FundingSourceEntity source;
  BrokerAccountEntity account;
  FundingDepositRequest deposit;
  FundingWithdrawalRequest withdraw;
  FundingTransferRequest transfer;
  @BeforeEach void setup() {
    MDC.put("orgId", "org-a");
    source = new FundingSourceEntity(); source.setId("source"); source.setUserId("user");
    source.setOrgId("org-a"); source.setStatus(FundingStatus.VERIFIED); source.setProviderId("live");
    when(sources.findById("source")).thenReturn(Optional.of(source)); when(live.supports("live")).thenReturn(true);
    account = new BrokerAccountEntity(); account.setId("account"); account.setUserId("user"); account.setOrgId("org-a");
    when(accounts.findById("account")).thenReturn(Optional.of(account));
    deposit = new FundingDepositRequest(); deposit.setSourceId("source"); deposit.setAmount(10.0);
    withdraw = new FundingWithdrawalRequest(); withdraw.setSourceId("source"); withdraw.setAmount(10.0);
    transfer = new FundingTransferRequest(); transfer.setSourceId("source"); transfer.setAmount(10.0);
    transfer.setBrokerAccountId("account"); transfer.setDirection(FundingTransferDirection.TO_BROKER);
  }
  @AfterEach void cleanup() { MDC.clear(); }
  void status(int expected, Runnable action) {
    assertEquals(expected, assertThrows(ResponseStatusException.class, action::run).getStatusCode().value());
  }
  void noMovement() {
    verify(live, never()).deposit(any(), any()); verify(live, never()).withdraw(any(), any());
    verify(live, never()).transfer(any(), any(), any()); verifyNoInteractions(trade, deposits, withdrawals, transfers);
  }
  @Test void liveFundingRoutesAreClosedUntilPersistedApprovalExists() {
    status(409, () -> service.deposit("user", deposit));
    status(409, () -> service.withdraw("user", withdraw));
    status(409, () -> service.transfer("user", transfer)); noMovement();
  }
  @Test void wrongTenantSourceCannotBeUsedForAnyMovement() {
    source.setOrgId("org-b");
    status(404, () -> service.deposit("user", deposit)); status(404, () -> service.withdraw("user", withdraw));
    status(404, () -> service.transfer("user", transfer)); noMovement(); verifyNoInteractions(live);
  }
  @Test void missingTenantCannotAdoptOrganizationSource() {
    MDC.clear(); status(404, () -> service.deposit("user", deposit)); noMovement();
  }
  @Test void crossTenantBrokerDestinationIsRejectedBeforeAdapter() {
    account.setOrgId("org-b"); status(404, () -> service.transfer("user", transfer)); noMovement(); verifyNoInteractions(live);
  }
}
