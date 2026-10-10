package com.alphamath.portfolio.application.banking;

import com.alphamath.portfolio.application.audit.AuditService;
import com.alphamath.portfolio.application.trade.TradeService;
import com.alphamath.portfolio.domain.banking.BankingTransfer;
import com.alphamath.portfolio.domain.banking.BankingTransferDirection;
import com.alphamath.portfolio.domain.banking.BankingTransferRequest;
import com.alphamath.portfolio.domain.banking.BankingTransferStatus;
import com.alphamath.portfolio.infrastructure.persistence.BankingAccountEntity;
import com.alphamath.portfolio.infrastructure.persistence.BankingAccountRepository;
import com.alphamath.portfolio.infrastructure.persistence.BankingTransferEntity;
import com.alphamath.portfolio.infrastructure.persistence.BankingTransferRepository;
import com.alphamath.portfolio.security.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BankingServiceTransferInvariantTest {

  @Mock
  private BankingAccountRepository accounts;
  @Mock
  private BankingTransferRepository transfers;
  @Mock
  private TradeService trade;
  @Mock
  private AuditService audit;

  private BankingService service;

  @BeforeEach
  void setUp() {
    TenantContext tenantContext = new TenantContext();
    service = new BankingService(accounts, transfers, trade, audit, tenantContext, "USD");
  }

  @Test
  void transferRejectsNonPositiveAmount() {
    BankingTransferRequest req = request(BankingTransferDirection.TO_INVESTING, 0.0, "USD");

    ResponseStatusException ex = assertThrows(ResponseStatusException.class,
        () -> service.transfer("user-1", req));

    assertEquals(400, ex.getStatusCode().value());
    verify(accounts, never()).findByUserId(any());
    verify(transfers, never()).save(any(BankingTransferEntity.class));
    verify(trade, never()).creditCash(any(), any(Double.class));
    verify(trade, never()).debitCash(any(), any(Double.class));
  }

  @Test
  void transferRejectsCurrencyMismatchBeforeFundsMove() {
    when(accounts.findByUserId("user-1")).thenReturn(Optional.of(accountEntity(500.0, "USD")));
    BankingTransferRequest req = request(BankingTransferDirection.TO_INVESTING, 100.0, "EUR");

    ResponseStatusException ex = assertThrows(ResponseStatusException.class,
        () -> service.transfer("user-1", req));

    assertEquals(400, ex.getStatusCode().value());
    verify(accounts, never()).save(any(BankingAccountEntity.class));
    verify(transfers, never()).save(any(BankingTransferEntity.class));
    verify(trade, never()).creditCash(any(), any(Double.class));
    verify(trade, never()).debitCash(any(), any(Double.class));
  }

  @Test
  void toInvestingInsufficientBalancePersistsFailedTransferAndAudit() {
    when(accounts.findByUserId("user-1")).thenReturn(Optional.of(accountEntity(50.0, "USD")));
    BankingTransferRequest req = request(BankingTransferDirection.TO_INVESTING, 100.0, "USD");

    ResponseStatusException ex = assertThrows(ResponseStatusException.class,
        () -> service.transfer("user-1", req));

    assertEquals(400, ex.getStatusCode().value());
    ArgumentCaptor<BankingTransferEntity> transferCaptor = ArgumentCaptor.forClass(BankingTransferEntity.class);
    verify(transfers).save(transferCaptor.capture());
    BankingTransferEntity saved = transferCaptor.getValue();
    assertEquals(BankingTransferStatus.FAILED.name(), saved.getStatus());
    assertEquals(BankingTransferDirection.TO_INVESTING.name(), saved.getDirection());
    assertEquals(100.0, saved.getAmount());
    verify(accounts, never()).save(any(BankingAccountEntity.class));
    verify(trade, never()).creditCash(any(), any(Double.class));
    verify(audit).record(
        eq("user-1"),
        eq("user-1"),
        eq("BANKING_TRANSFER_FAILED"),
        eq("portfolio_banking_transfer"),
        eq(saved.getId()),
        anyMap()
    );
  }

  @Test
  void toInvestingSuccessDebitsBankingAndCreditsTradeCash() {
    when(accounts.findByUserId("user-1")).thenReturn(Optional.of(accountEntity(500.0, "USD")));
    BankingTransferRequest req = request(BankingTransferDirection.TO_INVESTING, 100.0, "USD");

    BankingTransfer result = service.transfer("user-1", req);

    assertEquals(BankingTransferStatus.COMPLETED, result.getStatus());
    assertEquals(100.0, result.getAmount());
    assertEquals(BankingTransferDirection.TO_INVESTING, result.getDirection());
    ArgumentCaptor<BankingAccountEntity> accountCaptor = ArgumentCaptor.forClass(BankingAccountEntity.class);
    verify(accounts).save(accountCaptor.capture());
    assertEquals(400.0, accountCaptor.getValue().getCash());
    verify(trade).creditCash("user-1", 100.0);
    ArgumentCaptor<BankingTransferEntity> transferCaptor = ArgumentCaptor.forClass(BankingTransferEntity.class);
    verify(transfers).save(transferCaptor.capture());
    assertEquals(BankingTransferStatus.COMPLETED.name(), transferCaptor.getValue().getStatus());
    verify(audit).record(
        eq("user-1"),
        eq("user-1"),
        eq("BANKING_TRANSFER"),
        eq("portfolio_banking_transfer"),
        eq(transferCaptor.getValue().getId()),
        anyMap()
    );
  }

  @Test
  void toBankingDebitFailurePersistsFailedTransferWithoutSavingBankingBalance() {
    when(accounts.findByUserId("user-1")).thenReturn(Optional.of(accountEntity(200.0, "USD")));
    BankingTransferRequest req = request(BankingTransferDirection.FROM_INVESTING, 150.0, "USD");
    ResponseStatusException debitFailure =
        new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "insufficient cash balance");
    org.mockito.Mockito.doThrow(debitFailure).when(trade).debitCash("user-1", 150.0);

    ResponseStatusException ex = assertThrows(ResponseStatusException.class,
        () -> service.transfer("user-1", req));

    assertEquals(400, ex.getStatusCode().value());
    verify(accounts, never()).save(any(BankingAccountEntity.class));
    ArgumentCaptor<BankingTransferEntity> transferCaptor = ArgumentCaptor.forClass(BankingTransferEntity.class);
    verify(transfers).save(transferCaptor.capture());
    assertEquals(BankingTransferStatus.FAILED.name(), transferCaptor.getValue().getStatus());
    verify(audit).record(
        eq("user-1"),
        eq("user-1"),
        eq("BANKING_TRANSFER_FAILED"),
        eq("portfolio_banking_transfer"),
        eq(transferCaptor.getValue().getId()),
        anyMap()
    );
  }

  @Test
  void toBankingSuccessDebitsTradeAndCreditsBankingCash() {
    when(accounts.findByUserId("user-1")).thenReturn(Optional.of(accountEntity(200.0, "USD")));
    BankingTransferRequest req = request(BankingTransferDirection.FROM_INVESTING, 50.0, "USD");

    BankingTransfer result = service.transfer("user-1", req);

    assertEquals(BankingTransferStatus.COMPLETED, result.getStatus());
    assertEquals(BankingTransferDirection.FROM_INVESTING, result.getDirection());
    verify(trade).debitCash("user-1", 50.0);
    ArgumentCaptor<BankingAccountEntity> accountCaptor = ArgumentCaptor.forClass(BankingAccountEntity.class);
    verify(accounts).save(accountCaptor.capture());
    assertEquals(250.0, accountCaptor.getValue().getCash());
    ArgumentCaptor<BankingTransferEntity> transferCaptor = ArgumentCaptor.forClass(BankingTransferEntity.class);
    verify(transfers).save(transferCaptor.capture());
    assertEquals(BankingTransferStatus.COMPLETED.name(), transferCaptor.getValue().getStatus());
    verify(audit).record(
        eq("user-1"),
        eq("user-1"),
        eq("BANKING_TRANSFER"),
        eq("portfolio_banking_transfer"),
        eq(transferCaptor.getValue().getId()),
        anyMap()
    );
  }

  private BankingTransferRequest request(BankingTransferDirection direction, double amount, String currency) {
    BankingTransferRequest req = new BankingTransferRequest();
    req.setDirection(direction);
    req.setAmount(amount);
    req.setCurrency(currency);
    req.setNote("test transfer");
    return req;
  }

  private BankingAccountEntity accountEntity(double cash, String currency) {
    BankingAccountEntity account = new BankingAccountEntity();
    account.setId("bank-acct-1");
    account.setUserId("user-1");
    account.setStatus("ACTIVE");
    account.setCash(cash);
    account.setCurrency(currency);
    account.setCreatedAt(Instant.parse("2026-03-13T09:00:00Z"));
    account.setUpdatedAt(Instant.parse("2026-03-13T09:00:00Z"));
    return account;
  }
}
