package com.alphamath.portfolio.application.banking;

import com.alphamath.portfolio.application.audit.AuditService;
import com.alphamath.portfolio.application.trade.TradeService;
import com.alphamath.portfolio.domain.banking.BankingAccount;
import com.alphamath.portfolio.domain.banking.BankingTransfer;
import com.alphamath.portfolio.domain.banking.BankingTransferDirection;
import com.alphamath.portfolio.domain.banking.BankingTransferRequest;
import com.alphamath.portfolio.domain.banking.BankingTransferStatus;
import com.alphamath.portfolio.infrastructure.persistence.BankingAccountEntity;
import com.alphamath.portfolio.infrastructure.persistence.BankingAccountRepository;
import com.alphamath.portfolio.infrastructure.persistence.BankingTransferEntity;
import com.alphamath.portfolio.infrastructure.persistence.BankingTransferRepository;
import com.alphamath.portfolio.security.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class BankingService {
  private static final String STATUS_ACTIVE = "ACTIVE";

  private final BankingAccountRepository accounts;
  private final BankingTransferRepository transfers;
  private final TradeService trade;
  private final AuditService audit;
  private final TenantContext tenantContext;
  private final String defaultCurrency;

  public BankingService(BankingAccountRepository accounts,
                        BankingTransferRepository transfers,
                        TradeService trade,
                        AuditService audit,
                        TenantContext tenantContext,
                        @Value("${alphamath.banking.defaultCurrency:USD}") String defaultCurrency) {
    this.accounts = accounts;
    this.transfers = transfers;
    this.trade = trade;
    this.audit = audit;
    this.tenantContext = tenantContext;
    this.defaultCurrency = defaultCurrency == null || defaultCurrency.isBlank() ? "USD" : defaultCurrency;
  }

  public BankingAccount getAccount(String userId) {
    BankingAccountEntity entity = getOrCreateAccount(userId);
    return toDto(entity);
  }

  public List<BankingTransfer> listTransfers(String userId, int limit) {
    int size = limit <= 0 ? 50 : Math.min(limit, 200);
    List<BankingTransfer> out = new ArrayList<>();
    String orgId = tenantContext.getOrgId();
    List<BankingTransferEntity> rows = orgId == null
        ? transfers.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, size))
        : transfers.findByUserIdAndOrgIdOrderByCreatedAtDesc(userId, orgId, PageRequest.of(0, size));
    for (BankingTransferEntity entity : rows) {
      if (tenantContext.owns(userId, entity.getUserId(), entity.getOrgId())) out.add(toDto(entity));
    }
    return out;
  }

  @Transactional
  public BankingTransfer transfer(String userId, BankingTransferRequest req) {
    if (req == null || req.getDirection() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "direction is required");
    }
    double amount = req.getAmount() == null ? 0.0 : req.getAmount();
    if (amount <= 0.0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount must be > 0");
    }

    BankingAccountEntity account = getOrCreateAccount(userId);
    String currency = req.getCurrency() == null || req.getCurrency().isBlank()
        ? account.getCurrency()
        : req.getCurrency().trim().toUpperCase();
    if (!currency.equalsIgnoreCase(account.getCurrency())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "currency mismatch");
    }

    BankingTransferEntity entity = new BankingTransferEntity();
    entity.setId(UUID.randomUUID().toString());
    entity.setUserId(userId);
    entity.setOrgId(tenantContext.getOrgId());
    entity.setDirection(req.getDirection().name());
    entity.setAmount(amount);
    entity.setCurrency(currency);
    entity.setNote(req.getNote());
    entity.setCreatedAt(Instant.now());

    try {
      if (req.getDirection() == BankingTransferDirection.TO_INVESTING) {
        if (account.getCash() < amount) {
          throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "insufficient banking balance");
        }
        account.setCash(account.getCash() - amount);
        account.setUpdatedAt(Instant.now());
        accounts.save(account);
        trade.creditCash(userId, amount);
      } else {
        trade.debitCash(userId, amount);
        account.setCash(account.getCash() + amount);
        account.setUpdatedAt(Instant.now());
        accounts.save(account);
      }
      entity.setStatus(BankingTransferStatus.COMPLETED.name());
    } catch (ResponseStatusException e) {
      entity.setStatus(BankingTransferStatus.FAILED.name());
      transfers.save(entity);
      audit.record(userId, userId, "BANKING_TRANSFER_FAILED", "portfolio_banking_transfer", entity.getId(),
          java.util.Map.of("direction", entity.getDirection(), "amount", amount));
      throw e;
    }

    transfers.save(entity);
    audit.record(userId, userId, "BANKING_TRANSFER", "portfolio_banking_transfer", entity.getId(),
        java.util.Map.of("direction", entity.getDirection(), "amount", amount));
    return toDto(entity);
  }

  private BankingAccountEntity getOrCreateAccount(String userId) {
    String orgId = tenantContext.getOrgId();
    BankingAccountEntity found = (orgId == null ? accounts.findByUserId(userId) : accounts.findByUserIdAndOrgId(userId, orgId)).orElseGet(() -> {
      BankingAccountEntity created = new BankingAccountEntity();
      created.setId(UUID.randomUUID().toString());
      created.setUserId(userId);
      created.setOrgId(tenantContext.getOrgId());
      created.setStatus(STATUS_ACTIVE);
      created.setCash(0.0);
      created.setCurrency(defaultCurrency);
      created.setCreatedAt(Instant.now());
      created.setUpdatedAt(created.getCreatedAt());
      accounts.save(created);
      audit.record(userId, userId, "BANKING_ACCOUNT_CREATED", "portfolio_banking_account", created.getId(),
          java.util.Map.of("currency", created.getCurrency()));
      return created;
    });
    if (!tenantContext.owns(userId, found.getUserId(), found.getOrgId())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Banking account not found");
    }
    return found;
  }

  private BankingAccount toDto(BankingAccountEntity entity) {
    BankingAccount out = new BankingAccount();
    out.setId(entity.getId());
    out.setUserId(entity.getUserId());
    out.setStatus(entity.getStatus());
    out.setCash(entity.getCash());
    out.setCurrency(entity.getCurrency());
    out.setCreatedAt(entity.getCreatedAt());
    out.setUpdatedAt(entity.getUpdatedAt());
    return out;
  }

  private BankingTransfer toDto(BankingTransferEntity entity) {
    BankingTransfer out = new BankingTransfer();
    out.setId(entity.getId());
    out.setUserId(entity.getUserId());
    out.setDirection(BankingTransferDirection.valueOf(entity.getDirection()));
    out.setAmount(entity.getAmount());
    out.setCurrency(entity.getCurrency());
    out.setStatus(BankingTransferStatus.valueOf(entity.getStatus()));
    out.setNote(entity.getNote());
    out.setCreatedAt(entity.getCreatedAt());
    return out;
  }
}
