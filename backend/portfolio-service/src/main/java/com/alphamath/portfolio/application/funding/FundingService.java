package com.alphamath.portfolio.application.funding;

import com.alphamath.portfolio.application.audit.AuditService;
import com.alphamath.portfolio.application.trade.TradeService;
import com.alphamath.portfolio.domain.execution.BrokerAccount;
import com.alphamath.portfolio.domain.funding.FundingDepositReceipt;
import com.alphamath.portfolio.domain.funding.FundingDepositRequest;
import com.alphamath.portfolio.domain.funding.FundingLinkRequest;
import com.alphamath.portfolio.domain.funding.FundingMethodType;
import com.alphamath.portfolio.domain.funding.FundingProviderInfo;
import com.alphamath.portfolio.domain.funding.FundingSource;
import com.alphamath.portfolio.domain.funding.FundingStatus;
import com.alphamath.portfolio.domain.funding.FundingTransactionStatus;
import com.alphamath.portfolio.domain.funding.FundingTransferDirection;
import com.alphamath.portfolio.domain.funding.FundingTransferReceipt;
import com.alphamath.portfolio.domain.funding.FundingTransferRequest;
import com.alphamath.portfolio.domain.funding.FundingVerifyRequest;
import com.alphamath.portfolio.domain.funding.FundingWithdrawalReceipt;
import com.alphamath.portfolio.domain.funding.FundingWithdrawalRequest;
import com.alphamath.portfolio.infrastructure.persistence.BrokerAccountEntity;
import com.alphamath.portfolio.infrastructure.persistence.BrokerAccountRepository;
import com.alphamath.portfolio.infrastructure.persistence.FundingDepositEntity;
import com.alphamath.portfolio.infrastructure.persistence.FundingDepositRepository;
import com.alphamath.portfolio.infrastructure.persistence.FundingSourceEntity;
import com.alphamath.portfolio.infrastructure.persistence.FundingSourceRepository;
import com.alphamath.portfolio.infrastructure.persistence.FundingTransferEntity;
import com.alphamath.portfolio.infrastructure.persistence.FundingTransferRepository;
import com.alphamath.portfolio.infrastructure.persistence.FundingWithdrawalEntity;
import com.alphamath.portfolio.infrastructure.persistence.FundingWithdrawalRepository;
import com.alphamath.portfolio.security.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class FundingService {
  private final TradeService trade;
  private final FundingSourceRepository sources;
  private final FundingDepositRepository deposits;
  private final FundingWithdrawalRepository withdrawals;
  private final FundingTransferRepository transfers;
  private final BrokerAccountRepository brokerAccounts;
  private final List<FundingAdapter> adapters;
  private final AuditService audit;
  private final TenantContext tenantContext;

  public FundingService(TradeService trade,
                        FundingSourceRepository sources,
                        FundingDepositRepository deposits,
                        FundingWithdrawalRepository withdrawals,
                        FundingTransferRepository transfers,
                        BrokerAccountRepository brokerAccounts,
                        List<FundingAdapter> adapters,
                        AuditService audit,
                        TenantContext tenantContext) {
    this.trade = trade;
    this.sources = sources;
    this.deposits = deposits;
    this.withdrawals = withdrawals;
    this.transfers = transfers;
    this.brokerAccounts = brokerAccounts;
    this.adapters = adapters;
    this.audit = audit;
    this.tenantContext = tenantContext;
  }

  public List<FundingProviderInfo> listProviders() {
    List<FundingProviderInfo> out = new ArrayList<>();
    out.add(provider("stripe", "Stripe", List.of(FundingMethodType.CARD), List.of("tokenized_card"), "Global"));
    out.add(provider("adyen", "Adyen", List.of(FundingMethodType.CARD), List.of("tokenized_card"), "Global"));
    out.add(provider("plaid", "Plaid", List.of(FundingMethodType.BANK_ACH, FundingMethodType.BANK_ACCOUNT), List.of("oauth", "micro_deposit"), "US/CA/EU"));
    out.add(provider("dwolla", "Dwolla", List.of(FundingMethodType.BANK_ACH), List.of("micro_deposit"), "US"));
    out.add(provider("wise", "Wise", List.of(FundingMethodType.BANK_WIRE), List.of("wire_reference"), "Global"));
    out.add(provider("paypal", "PayPal", List.of(FundingMethodType.PAYPAL), List.of("oauth"), "Global"));
    out.add(provider("coinbase", "Coinbase", List.of(FundingMethodType.CRYPTO), List.of("wallet_connect"), "Global"));
    out.add(provider("fireblocks", "Fireblocks", List.of(FundingMethodType.CRYPTO), List.of("wallet_whitelist"), "Enterprise"));
    return out;
  }

  public List<FundingSource> listSources(String userId) {
    List<FundingSource> out = new ArrayList<>();
    String orgId = tenantContext.getOrgId();
    List<FundingSourceEntity> rows = orgId == null
        ? sources.findByUserIdOrderByCreatedAtDesc(userId)
        : sources.findByUserIdAndOrgIdOrderByCreatedAtDesc(userId, orgId);
    for (FundingSourceEntity entity : rows) {
      if (tenantContext.owns(userId, entity.getUserId(), entity.getOrgId())) out.add(toDto(entity));
    }
    return out;
  }

  public FundingSource linkSource(String userId, FundingLinkRequest req) {
    if (req.getMethodType() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "methodType is required");
    }
    FundingSource source = new FundingSource();
    source.setId(UUID.randomUUID().toString());
    source.setUserId(userId);
    source.setMethodType(req.getMethodType());
    source.setProviderId(req.getProviderId());
    source.setProviderReference(req.getProviderReference());
    source.setLabel(req.getLabel());
    source.setLast4(req.getLast4() == null ? "0000" : req.getLast4());
    source.setCurrency(req.getCurrency() == null ? "USD" : req.getCurrency());
    source.setNetwork(req.getNetwork());
    source.setCreatedAt(Instant.now());

    FundingStatus status = switch (req.getMethodType()) {
      case CARD, PAYPAL, CRYPTO -> FundingStatus.VERIFIED;
      default -> FundingStatus.PENDING_VERIFICATION;
    };
    source.setStatus(status);

    FundingSourceEntity entity = toEntity(source);
    entity.setUpdatedAt(Instant.now());
    sources.save(entity);
    audit.record(userId, userId, "FUNDING_SOURCE_LINKED", "portfolio_funding_source", source.getId(), java.util.Map.of(
        "methodType", source.getMethodType().name(),
        "providerId", source.getProviderId() == null ? "" : source.getProviderId()
    ));
    return source;
  }

  public FundingSource verifySource(String userId, String id, FundingVerifyRequest req) {
    FundingSourceEntity entity = getSourceEntity(userId, id);
    if (entity.getStatus() == FundingStatus.DISABLED) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Source disabled");
    }
    entity.setStatus(FundingStatus.VERIFIED);
    entity.setUpdatedAt(Instant.now());
    sources.save(entity);
    audit.record(userId, userId, "FUNDING_SOURCE_VERIFIED", "portfolio_funding_source", entity.getId(), java.util.Map.of());
    return toDto(entity);
  }

  @Transactional
  public FundingDepositReceipt deposit(String userId, FundingDepositRequest req) {
    FundingSourceEntity source = getSourceEntity(userId, req.getSourceId());
    if (source.getStatus() != FundingStatus.VERIFIED) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Source not verified");
    }
    double amount = req.getAmount() == null ? 0.0 : req.getAmount();
    if (amount <= 0.0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount must be > 0");
    }

    FundingSource sourceDto = toDto(source);
    FundingAdapter adapter = adapterFor(sourceDto.getProviderId());
    if (!adapter.isSimulation()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Live funding requires a persisted approval workflow; only simulation is available");
    }
    FundingDepositReceipt receipt = adapter.deposit(sourceDto, req);
    receipt.setUserId(userId);
    if (receipt.getSourceId() == null) {
      receipt.setSourceId(source.getId());
    }
    if (receipt.getCurrency() == null || receipt.getCurrency().isBlank()) {
      receipt.setCurrency(source.getCurrency());
    }
    if (receipt.getProviderId() == null || receipt.getProviderId().isBlank()) {
      receipt.setProviderId(source.getProviderId());
    }
    if (receipt.getCreatedAt() == null) {
      receipt.setCreatedAt(Instant.now());
    }
    if (receipt.getUpdatedAt() == null) {
      receipt.setUpdatedAt(receipt.getCreatedAt());
    }
    if (receipt.getStatus() == null || receipt.getStatus().isBlank()) {
      receipt.setStatus(FundingTransactionStatus.PENDING.name());
    }

    if (FundingTransactionStatus.COMPLETED.name().equals(receipt.getStatus())) {
      trade.creditCash(userId, amount);
    }

    FundingDepositEntity entity = new FundingDepositEntity();
    entity.setId(receipt.getId());
    entity.setUserId(userId);
    entity.setOrgId(tenantContext.getOrgId());
    entity.setSourceId(receipt.getSourceId());
    entity.setAmount(receipt.getAmount());
    entity.setCurrency(receipt.getCurrency());
    entity.setStatus(receipt.getStatus());
    entity.setNote(receipt.getNote());
    entity.setCreatedAt(receipt.getCreatedAt());
    entity.setUpdatedAt(receipt.getUpdatedAt());
    entity.setProviderId(receipt.getProviderId());
    entity.setProviderReference(receipt.getProviderReference());
    deposits.save(entity);
    audit.record(userId, userId, "FUNDING_DEPOSIT", "portfolio_funding_deposit", receipt.getId(), java.util.Map.of(
        "amount", receipt.getAmount(),
        "sourceId", receipt.getSourceId()
    ));
    return receipt;
  }

  @Transactional
  public FundingWithdrawalReceipt withdraw(String userId, FundingWithdrawalRequest req) {
    FundingSourceEntity source = getSourceEntity(userId, req.getSourceId());
    if (source.getStatus() != FundingStatus.VERIFIED) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Source not verified");
    }
    double amount = req.getAmount() == null ? 0.0 : req.getAmount();
    if (amount <= 0.0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount must be > 0");
    }

    FundingSource sourceDto = toDto(source);
    FundingAdapter adapter = adapterFor(sourceDto.getProviderId());
    if (!adapter.isSimulation()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Live funding requires a persisted approval workflow; only simulation is available");
    }
    FundingWithdrawalReceipt receipt = adapter.withdraw(sourceDto, req);
    receipt.setUserId(userId);
    if (receipt.getSourceId() == null) {
      receipt.setSourceId(source.getId());
    }
    if (receipt.getCurrency() == null || receipt.getCurrency().isBlank()) {
      receipt.setCurrency(source.getCurrency());
    }
    if (receipt.getProviderId() == null || receipt.getProviderId().isBlank()) {
      receipt.setProviderId(source.getProviderId());
    }
    if (receipt.getCreatedAt() == null) {
      receipt.setCreatedAt(Instant.now());
    }
    if (receipt.getUpdatedAt() == null) {
      receipt.setUpdatedAt(receipt.getCreatedAt());
    }
    if (receipt.getStatus() == null || receipt.getStatus().isBlank()) {
      receipt.setStatus(FundingTransactionStatus.PENDING.name());
    }

    if (FundingTransactionStatus.COMPLETED.name().equals(receipt.getStatus())) {
      trade.debitCash(userId, amount);
    }

    FundingWithdrawalEntity entity = new FundingWithdrawalEntity();
    entity.setId(receipt.getId());
    entity.setUserId(userId);
    entity.setOrgId(tenantContext.getOrgId());
    entity.setSourceId(receipt.getSourceId());
    entity.setAmount(receipt.getAmount());
    entity.setCurrency(receipt.getCurrency());
    entity.setStatus(receipt.getStatus());
    entity.setNote(receipt.getNote());
    entity.setProviderId(receipt.getProviderId());
    entity.setProviderReference(receipt.getProviderReference());
    entity.setCreatedAt(receipt.getCreatedAt());
    entity.setUpdatedAt(receipt.getUpdatedAt());
    withdrawals.save(entity);
    audit.record(userId, userId, "FUNDING_WITHDRAWAL", "portfolio_funding_withdrawal", receipt.getId(), java.util.Map.of(
        "amount", receipt.getAmount(),
        "sourceId", receipt.getSourceId()
    ));
    return receipt;
  }

  @Transactional
  public FundingTransferReceipt transfer(String userId, FundingTransferRequest req) {
    FundingSourceEntity source = getSourceEntity(userId, req.getSourceId());
    if (source.getStatus() != FundingStatus.VERIFIED) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Source not verified");
    }
    if (req.getDirection() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "direction is required");
    }
    double amount = req.getAmount() == null ? 0.0 : req.getAmount();
    if (amount <= 0.0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount must be > 0");
    }

    BrokerAccountEntity brokerAccount = brokerAccounts.findById(req.getBrokerAccountId()).orElse(null);
    if (brokerAccount == null || !tenantContext.owns(userId, brokerAccount.getUserId(), brokerAccount.getOrgId())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Broker account not found");
    }

    FundingSource sourceDto = toDto(source);
    BrokerAccount brokerDto = toBrokerDto(brokerAccount);
    FundingAdapter adapter = adapterFor(sourceDto.getProviderId());
    if (!adapter.isSimulation()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Live funding requires a persisted approval workflow; only simulation is available");
    }
    FundingTransferReceipt receipt = adapter.transfer(sourceDto, brokerDto, req);
    receipt.setUserId(userId);
    if (receipt.getSourceId() == null) {
      receipt.setSourceId(source.getId());
    }
    if (receipt.getBrokerAccountId() == null) {
      receipt.setBrokerAccountId(brokerAccount.getId());
    }
    if (receipt.getCurrency() == null || receipt.getCurrency().isBlank()) {
      receipt.setCurrency(source.getCurrency());
    }
    if (receipt.getProviderId() == null || receipt.getProviderId().isBlank()) {
      receipt.setProviderId(source.getProviderId());
    }
    if (receipt.getCreatedAt() == null) {
      receipt.setCreatedAt(Instant.now());
    }
    if (receipt.getUpdatedAt() == null) {
      receipt.setUpdatedAt(receipt.getCreatedAt());
    }
    if (receipt.getStatus() == null || receipt.getStatus().isBlank()) {
      receipt.setStatus(FundingTransactionStatus.PENDING.name());
    }
    if (receipt.getDirection() == null) {
      receipt.setDirection(req.getDirection());
    }

    if (FundingTransactionStatus.COMPLETED.name().equals(receipt.getStatus())) {
      if (receipt.getDirection() == FundingTransferDirection.TO_BROKER) {
        trade.creditCash(userId, amount);
      } else if (receipt.getDirection() == FundingTransferDirection.FROM_BROKER) {
        trade.debitCash(userId, amount);
      }
    }

    FundingTransferEntity entity = new FundingTransferEntity();
    entity.setId(receipt.getId());
    entity.setUserId(userId);
    entity.setOrgId(tenantContext.getOrgId());
    entity.setSourceId(receipt.getSourceId());
    entity.setBrokerAccountId(receipt.getBrokerAccountId());
    entity.setDirection(receipt.getDirection().name());
    entity.setAmount(receipt.getAmount());
    entity.setCurrency(receipt.getCurrency());
    entity.setStatus(receipt.getStatus());
    entity.setNote(receipt.getNote());
    entity.setProviderId(receipt.getProviderId());
    entity.setProviderReference(receipt.getProviderReference());
    entity.setCreatedAt(receipt.getCreatedAt());
    entity.setUpdatedAt(receipt.getUpdatedAt());
    transfers.save(entity);
    audit.record(userId, userId, "FUNDING_TRANSFER", "portfolio_funding_transfer", receipt.getId(), java.util.Map.of(
        "amount", receipt.getAmount(),
        "direction", receipt.getDirection().name(),
        "brokerAccountId", receipt.getBrokerAccountId()
    ));
    return receipt;
  }

  public List<FundingDepositReceipt> listDeposits(String userId) {
    List<FundingDepositReceipt> out = new ArrayList<>();
    String orgId = tenantContext.getOrgId();
    List<FundingDepositEntity> rows = orgId == null
        ? deposits.findByUserIdOrderByCreatedAtDesc(userId)
        : deposits.findByUserIdAndOrgIdOrderByCreatedAtDesc(userId, orgId);
    for (FundingDepositEntity entity : rows) {
      FundingDepositReceipt receipt = new FundingDepositReceipt();
      receipt.setId(entity.getId());
      receipt.setUserId(entity.getUserId());
      receipt.setSourceId(entity.getSourceId());
      receipt.setAmount(entity.getAmount());
      receipt.setCurrency(entity.getCurrency());
      receipt.setStatus(entity.getStatus());
      receipt.setNote(entity.getNote());
      receipt.setProviderId(entity.getProviderId());
      receipt.setProviderReference(entity.getProviderReference());
      receipt.setCreatedAt(entity.getCreatedAt());
      receipt.setUpdatedAt(entity.getUpdatedAt());
      if (tenantContext.owns(userId, entity.getUserId(), entity.getOrgId())) out.add(receipt);
    }
    return out;
  }

  public List<FundingWithdrawalReceipt> listWithdrawals(String userId) {
    List<FundingWithdrawalReceipt> out = new ArrayList<>();
    String orgId = tenantContext.getOrgId();
    List<FundingWithdrawalEntity> rows = orgId == null
        ? withdrawals.findByUserIdOrderByCreatedAtDesc(userId)
        : withdrawals.findByUserIdAndOrgIdOrderByCreatedAtDesc(userId, orgId);
    for (FundingWithdrawalEntity entity : rows) {
      FundingWithdrawalReceipt receipt = new FundingWithdrawalReceipt();
      receipt.setId(entity.getId());
      receipt.setUserId(entity.getUserId());
      receipt.setSourceId(entity.getSourceId());
      receipt.setAmount(entity.getAmount());
      receipt.setCurrency(entity.getCurrency());
      receipt.setStatus(entity.getStatus());
      receipt.setNote(entity.getNote());
      receipt.setProviderId(entity.getProviderId());
      receipt.setProviderReference(entity.getProviderReference());
      receipt.setCreatedAt(entity.getCreatedAt());
      receipt.setUpdatedAt(entity.getUpdatedAt());
      if (tenantContext.owns(userId, entity.getUserId(), entity.getOrgId())) out.add(receipt);
    }
    return out;
  }

  public List<FundingTransferReceipt> listTransfers(String userId) {
    List<FundingTransferReceipt> out = new ArrayList<>();
    String orgId = tenantContext.getOrgId();
    List<FundingTransferEntity> rows = orgId == null
        ? transfers.findByUserIdOrderByCreatedAtDesc(userId)
        : transfers.findByUserIdAndOrgIdOrderByCreatedAtDesc(userId, orgId);
    for (FundingTransferEntity entity : rows) {
      FundingTransferReceipt receipt = new FundingTransferReceipt();
      receipt.setId(entity.getId());
      receipt.setUserId(entity.getUserId());
      receipt.setSourceId(entity.getSourceId());
      receipt.setBrokerAccountId(entity.getBrokerAccountId());
      receipt.setDirection(FundingTransferDirection.valueOf(entity.getDirection()));
      receipt.setAmount(entity.getAmount());
      receipt.setCurrency(entity.getCurrency());
      receipt.setStatus(entity.getStatus());
      receipt.setNote(entity.getNote());
      receipt.setProviderId(entity.getProviderId());
      receipt.setProviderReference(entity.getProviderReference());
      receipt.setCreatedAt(entity.getCreatedAt());
      receipt.setUpdatedAt(entity.getUpdatedAt());
      if (tenantContext.owns(userId, entity.getUserId(), entity.getOrgId())) out.add(receipt);
    }
    return out;
  }

  private FundingSourceEntity getSourceEntity(String userId, String id) {
    FundingSourceEntity source = sources.findById(id).orElse(null);
    String orgId = tenantContext.getOrgId();
    if (source == null || !tenantContext.owns(userId, source.getUserId(), source.getOrgId())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Source not found");
    }
    return source;
  }

  private FundingSource toDto(FundingSourceEntity entity) {
    FundingSource source = new FundingSource();
    source.setId(entity.getId());
    source.setUserId(entity.getUserId());
    source.setMethodType(entity.getMethodType());
    source.setProviderId(entity.getProviderId());
    source.setProviderReference(entity.getProviderReference());
    source.setLabel(entity.getLabel());
    source.setLast4(entity.getLast4());
    source.setCurrency(entity.getCurrency());
    source.setNetwork(entity.getNetwork());
    source.setStatus(entity.getStatus());
    source.setCreatedAt(entity.getCreatedAt());
    return source;
  }

  private FundingSourceEntity toEntity(FundingSource source) {
    FundingSourceEntity entity = new FundingSourceEntity();
    entity.setId(source.getId());
    entity.setUserId(source.getUserId());
    entity.setOrgId(tenantContext.getOrgId());
    entity.setMethodType(source.getMethodType());
    entity.setProviderId(source.getProviderId());
    entity.setProviderReference(source.getProviderReference());
    entity.setLabel(source.getLabel());
    entity.setLast4(source.getLast4());
    entity.setCurrency(source.getCurrency());
    entity.setNetwork(source.getNetwork());
    entity.setStatus(source.getStatus());
    entity.setCreatedAt(source.getCreatedAt());
    return entity;
  }

  private BrokerAccount toBrokerDto(BrokerAccountEntity entity) {
    BrokerAccount acct = new BrokerAccount();
    acct.setId(entity.getId());
    acct.setUserId(entity.getUserId());
    acct.setProviderId(entity.getProviderId());
    acct.setProviderName(entity.getProviderName());
    acct.setBrokerConnectionId(entity.getBrokerConnectionId());
    acct.setExternalAccountId(entity.getExternalAccountId());
    acct.setAccountNumber(entity.getAccountNumber());
    acct.setBaseCurrency(entity.getBaseCurrency());
    acct.setAccountType(entity.getAccountType());
    acct.setRegion(entity.getRegion());
    acct.setStatus(entity.getStatus());
    acct.setCreatedAt(entity.getCreatedAt());
    acct.setUpdatedAt(entity.getUpdatedAt());
    return acct;
  }

  private FundingAdapter adapterFor(String providerId) {
    for (FundingAdapter adapter : adapters) {
      if (adapter.supports(providerId)) {
        return adapter;
      }
    }
    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Funding provider not supported");
  }

  private FundingProviderInfo provider(String id, String name, List<FundingMethodType> methods, List<String> verification, String regions) {
    FundingProviderInfo info = new FundingProviderInfo();
    info.setId(id);
    info.setDisplayName(name);
    info.setMethods(new ArrayList<>(methods));
    info.setVerificationModes(new ArrayList<>(verification));
    info.setRegions(List.of(regions));
    info.setNotes("Scaffold only. No live money movement.");
    return info;
  }
}
