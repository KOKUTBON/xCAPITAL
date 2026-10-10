package com.alphamath.portfolio.application.execution;

import com.alphamath.portfolio.application.audit.AuditService;
import com.alphamath.portfolio.application.compliance.ComplianceService;
import com.alphamath.portfolio.infrastructure.persistence.TradeProposalRepository;
import org.springframework.transaction.annotation.Transactional;
import com.alphamath.portfolio.application.broker.BrokerIntegrationService;
import com.alphamath.portfolio.domain.execution.AssetClass;
import com.alphamath.portfolio.domain.execution.BrokerAccount;
import com.alphamath.portfolio.domain.execution.BrokerAccountStatus;
import com.alphamath.portfolio.domain.execution.BrokerLinkRequest;
import com.alphamath.portfolio.domain.execution.BrokerProviderInfo;
import com.alphamath.portfolio.domain.broker.BrokerConnection;
import com.alphamath.portfolio.domain.execution.ExecutionIntent;
import com.alphamath.portfolio.domain.execution.ExecutionOrder;
import com.alphamath.portfolio.domain.execution.ExecutionStatus;
import com.alphamath.portfolio.domain.execution.Region;
import com.alphamath.portfolio.domain.broker.BrokerOrder;
import com.alphamath.portfolio.domain.broker.BrokerOrderStatus;
import com.alphamath.portfolio.domain.trade.TradeProposal;
import com.alphamath.portfolio.infrastructure.persistence.BrokerAccountEntity;
import com.alphamath.portfolio.infrastructure.persistence.BrokerAccountRepository;
import com.alphamath.portfolio.infrastructure.persistence.ExecutionFillEntity;
import com.alphamath.portfolio.infrastructure.persistence.ExecutionFillRepository;
import com.alphamath.portfolio.infrastructure.persistence.ExecutionIntentEntity;
import com.alphamath.portfolio.infrastructure.persistence.ExecutionIntentRepository;
import com.alphamath.portfolio.infrastructure.persistence.JsonUtils;
import com.alphamath.portfolio.security.TenantContext;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ExecutionService {
  private final List<BrokerProviderInfo> providers = initProviders();
  private final BrokerAccountRepository accounts;
  private final ExecutionIntentRepository intents;
  private final ExecutionFillRepository fills;
  private final BrokerIntegrationService brokers;
  private final AuditService audit;
  private final TenantContext tenantContext;
  private final ExecutionApprovalGuard approval;
  private final TradeProposalRepository proposals;
  private final ComplianceService compliance;

  public ExecutionService(BrokerAccountRepository accounts,
                          ExecutionIntentRepository intents,
                          ExecutionFillRepository fills,
                          BrokerIntegrationService brokers,
                          AuditService audit,
                          TenantContext tenantContext,
                          ExecutionApprovalGuard approval,
                          TradeProposalRepository proposals,
                          ComplianceService compliance) {
    this.accounts = accounts;
    this.intents = intents;
    this.fills = fills;
    this.brokers = brokers;
    this.audit = audit;
    this.tenantContext = tenantContext;
    this.approval = approval;
    this.proposals = proposals;
    this.compliance = compliance;
  }

  public List<BrokerProviderInfo> listProviders(Region region, AssetClass assetClass) {
    if (region == null && assetClass == null) return new ArrayList<>(providers);

    List<BrokerProviderInfo> out = new ArrayList<>();
    for (BrokerProviderInfo p : providers) {
      if (region != null && !p.getRegions().contains(region)) continue;
      if (assetClass != null && !p.getAssetClasses().contains(assetClass)) continue;
      out.add(p);
    }
    out.sort(Comparator.comparingInt(BrokerProviderInfo::getScore).reversed());
    return out;
  }

  public BrokerAccount linkAccount(String userId, BrokerLinkRequest req) {
    BrokerProviderInfo provider = findProvider(req.getProviderId());
    BrokerConnection connection = brokers.connect(userId, provider.getId(), req.getLabel(), req.getMetadata());
    BrokerAccount acct = new BrokerAccount();
    acct.setId(UUID.randomUUID().toString());
    acct.setUserId(userId);
    acct.setProviderId(provider.getId());
    acct.setProviderName(provider.getDisplayName());
    acct.setBrokerConnectionId(connection.getId());
    acct.setRegion(req.getRegion());
    acct.setAssetClasses(req.getAssetClasses().isEmpty() ? provider.getAssetClasses() : req.getAssetClasses());
    acct.setStatus(BrokerAccountStatus.LINKED);
    acct.setCreatedAt(Instant.now());
    accounts.save(toEntity(acct));

    audit.record(userId, userId, "BROKER_LINKED", "portfolio_broker_account", acct.getId(),
        Map.of("providerId", acct.getProviderId(), "region", acct.getRegion().name()));
    return acct;
  }

  public List<BrokerAccount> listAccounts(String userId) {
    List<BrokerAccount> out = new ArrayList<>();
    String orgId = tenantContext.getOrgId();
    List<BrokerAccountEntity> rows = orgId == null
        ? accounts.findByUserIdOrderByCreatedAtDesc(userId)
        : accounts.findByUserIdAndOrgIdOrderByCreatedAtDesc(userId, orgId);
    for (BrokerAccountEntity entity : rows) {
      if (tenantContext.owns(userId, entity.getUserId(), entity.getOrgId())) out.add(toDto(entity));
    }
    return out;
  }

  public boolean hasLinkedAccount(String userId, Region region, AssetClass assetClass, String providerPreference) {
    String orgId = tenantContext.getOrgId();
    List<BrokerAccountEntity> rows = orgId == null
        ? accounts.findByUserIdOrderByCreatedAtDesc(userId)
        : accounts.findByUserIdAndOrgIdOrderByCreatedAtDesc(userId, orgId);
    for (BrokerAccountEntity entity : rows) {
      if (!tenantContext.owns(userId, entity.getUserId(), entity.getOrgId())) continue;
      BrokerAccount acct = toDto(entity);
      if (providerPreference != null && !providerPreference.isBlank() && !acct.getProviderId().equals(providerPreference)) {
        continue;
      }
      if (region != null && acct.getRegion() != region) continue;
      if (assetClass != null && !acct.getAssetClasses().contains(assetClass)) continue;
      if (acct.getStatus() == BrokerAccountStatus.LINKED) return true;
    }
    return false;
  }

  @Transactional
  public ExecutionIntent createIntent(String userId, TradeProposal proposal) {
    if (proposal == null || proposal.getId() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "proposal id is required");
    }
    var locked = proposals.findByIdForUpdate(proposal.getId()).orElse(null);
    if (locked == null || !tenantContext.owns(userId, locked.getUserId(), locked.getOrgId())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Proposal not found");
    }
    var approved = approval.requireApproved(userId, locked.getId());
    if (approved.getExecutionIntentId() != null) {
      ExecutionIntentEntity existing = intents.findById(approved.getExecutionIntentId()).orElse(null);
      approval.requireBoundIntent(userId, existing);
      return toDto(existing);
    }
    compliance.requireLiveEligibility(userId);
    BrokerProviderInfo provider = selectProvider(approved.getProviderPreference(), approved.getRegion(), approved.getAssetClass());
    BrokerAccount acct = findLinkedAccount(userId, provider.getId(), approved.getRegion(), approved.getAssetClass());
    ExecutionIntent intent = new ExecutionIntent();
    intent.setId(UUID.randomUUID().toString());
    intent.setUserId(userId);
    intent.setProposalId(approved.getId());
    intent.setProviderId(provider.getId());
    intent.setProviderName(provider.getDisplayName());
    intent.setRegion(approved.getRegion());
    intent.setAssetClass(approved.getAssetClass());
    intent.setStatus(ExecutionStatus.AUTHORIZED);
    intent.setCreatedAt(Instant.now());
    intent.setNote("Approved intent awaiting submission.");
    intent.setOrders(approval.executionOrders(approved));
    ExecutionIntentEntity saved = toEntity(intent);
    saved.setApprovalHash(approved.getApprovalHash());
    saved.setBrokerAccountId(acct.getId());
    intents.saveAndFlush(saved);
    approved.setExecutionIntentId(intent.getId());
    proposals.saveAndFlush(approved);
    audit.record(userId, userId, "EXECUTION_INTENT_CREATED", "portfolio_execution_intent", intent.getId(),
        Map.of("proposalId", approved.getId(), "approvalHash", approved.getApprovalHash()));
    return intent;
  }

  public ExecutionIntent getIntent(String userId, String id) {
    ExecutionIntentEntity entity = intents.findById(id).orElse(null);
    String orgId = tenantContext.getOrgId();
    if (entity == null || !tenantContext.owns(userId, entity.getUserId(), entity.getOrgId())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Execution intent not found");
    }
    return toDto(entity);
  }

  public List<ExecutionIntent> listIntents(String userId) {
    List<ExecutionIntent> out = new ArrayList<>();
    String orgId = tenantContext.getOrgId();
    List<ExecutionIntentEntity> rows = orgId == null
        ? intents.findByUserIdOrderByCreatedAtDesc(userId)
        : intents.findByUserIdAndOrgIdOrderByCreatedAtDesc(userId, orgId);
    for (ExecutionIntentEntity entity : rows) {
      if (tenantContext.owns(userId, entity.getUserId(), entity.getOrgId())) out.add(toDto(entity));
    }
    return out;
  }

  public ExecutionIntent submitIntent(String userId, String id) {
    ExecutionIntentEntity entity = intents.findById(id).orElse(null);
    String orgId = tenantContext.getOrgId();
    if (entity == null || !tenantContext.owns(userId, entity.getUserId(), entity.getOrgId())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Execution intent not found");
    }
    approval.requireBoundIntent(userId, entity);
    compliance.requireLiveEligibility(userId);
    if (entity.getStatus() != ExecutionStatus.AUTHORIZED
        || intents.claimSubmission(id, userId, entity.getOrgId(), entity.getApprovalHash(),
            ExecutionStatus.AUTHORIZED, ExecutionStatus.SUBMITTING) != 1) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Execution intent is already claimed or submitted");
    }
    entity.setStatus(ExecutionStatus.SUBMITTING);
    ExecutionIntent intent = toDto(entity);
    try {
      List<String> brokerOrderIds = new ArrayList<>();
      List<BrokerOrder> placed = new ArrayList<>();
      for (int index = 0; index < intent.getOrders().size(); index++) {
        BrokerOrder brokerOrder = brokers.placeApprovedOrder(userId, entity.getBrokerAccountId(), id, index);
        placed.add(brokerOrder);
        if (brokerOrder.getId() != null) {
          brokerOrderIds.add(brokerOrder.getId());
        }
      }

      intent.setBrokerOrderIds(brokerOrderIds);
      ExecutionStatus status = deriveStatus(placed);
      intent.setStatus(status);
      intent.setNote(status == ExecutionStatus.FILLED
          ? "Execution filled via " + intent.getProviderName()
          : "Execution submitted to " + intent.getProviderName());

      if (status == ExecutionStatus.FILLED || status == ExecutionStatus.PARTIALLY_FILLED) {
        List<ExecutionFillEntity> fillRows = new ArrayList<>();
        Instant now = Instant.now();
        for (int i = 0; i < placed.size(); i++) {
          BrokerOrder brokerOrder = placed.get(i);
          if (brokerOrder.getStatus() != BrokerOrderStatus.FILLED) {
            continue;
          }
          ExecutionOrder execOrder = intent.getOrders().size() > i ? intent.getOrders().get(i) : null;
          ExecutionFillEntity fill = new ExecutionFillEntity();
          fill.setId(UUID.randomUUID().toString());
          fill.setIntentId(intent.getId());
          fill.setUserId(intent.getUserId());
          fill.setOrgId(tenantContext.getOrgId());
          fill.setProposalId(intent.getProposalId());
          fill.setSymbol(execOrder == null ? null : execOrder.getSymbol());
          fill.setSide(brokerOrder.getSide() == null ? "BUY" : brokerOrder.getSide().name());
          fill.setQuantity(brokerOrder.getFilledQuantity() == null ? 0.0 : brokerOrder.getFilledQuantity());
          fill.setPrice(brokerOrder.getAvgPrice() == null ? 0.0 : brokerOrder.getAvgPrice());
          fill.setFee(0.0);
          fill.setStatus(ExecutionStatus.FILLED.name());
          fill.setFilledAt(now);
          fill.setCreatedAt(now);
          fill.setNote("Live fill");
          fillRows.add(fill);
        }
        if (!fillRows.isEmpty()) {
          fills.saveAll(fillRows);
        }
      }

      applyIntentUpdate(entity, intent);
      intents.save(entity);
      audit.record(userId, userId, "EXECUTION_INTENT_SUBMITTED", "portfolio_execution_intent", intent.getId(),
          Map.of("brokerOrders", brokerOrderIds.size(), "status", intent.getStatus().name()));
      return intent;
    } catch (RuntimeException e) {
      // Keep the durable claim: external acceptance may precede a timeout/local failure.
      intents.markSubmissionUnknown(id, ExecutionStatus.SUBMITTING, ExecutionStatus.SUBMISSION_UNKNOWN);
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
          "Submission outcome requires investigation; do not retry this intent", e);
    }
  }

  public ExecutionIntent simulateFill(String userId, String id) {
    ExecutionIntentEntity entity = intents.findById(id).orElse(null);
    if (entity == null || !tenantContext.owns(userId, entity.getUserId(), entity.getOrgId())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Execution intent not found");
    }
    throw new ResponseStatusException(HttpStatus.CONFLICT,
        "Execution fills must come from broker status; synthetic intent fills are disabled");
  }

  private BrokerProviderInfo selectProvider(String preference, Region region, AssetClass assetClass) {
    if (preference != null && !preference.isBlank()) {
      return findProvider(preference);
    }
    List<BrokerProviderInfo> candidates = listProviders(region, assetClass);
    if (candidates.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No providers for region/asset class");
    }
    return candidates.get(0);
  }

  private BrokerAccount findLinkedAccount(String userId, String providerId, Region region, AssetClass assetClass) {
    String orgId = tenantContext.getOrgId();
    List<BrokerAccountEntity> rows = orgId == null
        ? accounts.findByUserIdOrderByCreatedAtDesc(userId)
        : accounts.findByUserIdAndOrgIdOrderByCreatedAtDesc(userId, orgId);
    for (BrokerAccountEntity entity : rows) {
      if (!tenantContext.owns(userId, entity.getUserId(), entity.getOrgId())) continue;
      BrokerAccount acct = toDto(entity);
      if (!acct.getProviderId().equals(providerId)) continue;
      if (region != null && acct.getRegion() != region) continue;
      if (assetClass != null && !acct.getAssetClasses().contains(assetClass)) continue;
      if (acct.getStatus() == BrokerAccountStatus.LINKED) return acct;
    }
    throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "Broker account not linked");
  }

  private BrokerProviderInfo findProvider(String id) {
    for (BrokerProviderInfo info : providers) {
      if (info.getId().equals(id)) return info;
    }
    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Provider not found");
  }

  private List<BrokerProviderInfo> initProviders() {
    List<BrokerProviderInfo> out = new ArrayList<>();
    out.add(provider("interactive_brokers", "Interactive Brokers",
        List.of(Region.US, Region.EU, Region.UK, Region.APAC),
        List.of(AssetClass.EQUITY, AssetClass.ETF, AssetClass.FX, AssetClass.FIXED_INCOME, AssetClass.OPTIONS,
            AssetClass.FUTURES, AssetClass.COMMODITIES),
        List.of("global_access", "multi_asset", "prime_execution"), 95));
    out.add(provider("apex", "Apex Clearing",
        List.of(Region.US),
        List.of(AssetClass.EQUITY, AssetClass.ETF),
        List.of("clearing", "fractional_shares"), 90));
    out.add(provider("drivewealth", "DriveWealth",
        List.of(Region.US, Region.EU, Region.LATAM),
        List.of(AssetClass.EQUITY, AssetClass.ETF),
        List.of("global_fractional", "broker_api"), 88));
    out.add(provider("alpaca", "Alpaca",
        List.of(Region.US),
        List.of(AssetClass.EQUITY, AssetClass.ETF),
        List.of("commission_free", "paper_trading"), 80));
    out.add(provider("tradier", "Tradier",
        List.of(Region.US),
        List.of(AssetClass.EQUITY, AssetClass.ETF, AssetClass.OPTIONS),
        List.of("options_support"), 78));
    out.add(provider("jp_morgan", "JP Morgan",
        List.of(Region.US, Region.EU, Region.UK),
        List.of(AssetClass.EQUITY, AssetClass.ETF, AssetClass.FIXED_INCOME, AssetClass.MUTUAL_FUND, AssetClass.OPTIONS),
        List.of("managed_portfolios", "banking", "research", "fractional", "instant_transfer"), 88));
    out.add(provider("coinbase_prime", "Coinbase Prime",
        List.of(Region.GLOBAL),
        List.of(AssetClass.CRYPTO),
        List.of("institutional_custody", "best_execution"), 92));
    out.add(provider("fireblocks", "Fireblocks",
        List.of(Region.GLOBAL),
        List.of(AssetClass.CRYPTO),
        List.of("institutional_custody", "policy_engine"), 90));
    out.add(provider("kraken", "Kraken",
        List.of(Region.GLOBAL),
        List.of(AssetClass.CRYPTO),
        List.of("deep_liquidity"), 85));
    out.sort(Comparator.comparingInt(BrokerProviderInfo::getScore).reversed());
    return out;
  }

  private BrokerProviderInfo provider(String id, String name, List<Region> regions, List<AssetClass> assets,
                                      List<String> features, int score) {
    BrokerProviderInfo info = new BrokerProviderInfo();
    info.setId(id);
    info.setDisplayName(name);
    info.setRegions(new ArrayList<>(regions));
    info.setAssetClasses(new ArrayList<>(assets));
    info.setFeatures(new ArrayList<>(features));
    info.setScore(score);
    info.setNotes("Scaffold only. Live execution requires provider integration and approvals.");
    return info;
  }

  private BrokerAccountEntity toEntity(BrokerAccount acct) {
    BrokerAccountEntity entity = new BrokerAccountEntity();
    entity.setId(acct.getId());
    entity.setUserId(acct.getUserId());
    entity.setOrgId(tenantContext.getOrgId());
    entity.setProviderId(acct.getProviderId());
    entity.setProviderName(acct.getProviderName());
    entity.setBrokerConnectionId(acct.getBrokerConnectionId());
    entity.setExternalAccountId(acct.getExternalAccountId());
    entity.setAccountNumber(acct.getAccountNumber());
    entity.setBaseCurrency(acct.getBaseCurrency());
    entity.setAccountType(acct.getAccountType());
    entity.setRegion(acct.getRegion());
    entity.setAssetClassesJson(JsonUtils.toJson(acct.getAssetClasses()));
    entity.setPermissionsJson(JsonUtils.toJson(acct.getPermissions()));
    entity.setBalancesJson(JsonUtils.toJson(acct.getBalances()));
    entity.setStatus(acct.getStatus());
    entity.setCreatedAt(acct.getCreatedAt());
    entity.setUpdatedAt(acct.getUpdatedAt());
    return entity;
  }

  private BrokerAccount toDto(BrokerAccountEntity entity) {
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
    acct.setAssetClasses(JsonUtils.fromJson(entity.getAssetClassesJson(), new TypeReference<List<AssetClass>>() {}));
    acct.setPermissions(parseStringList(entity.getPermissionsJson()));
    acct.setBalances(parseBalances(entity.getBalancesJson()));
    acct.setStatus(entity.getStatus());
    acct.setCreatedAt(entity.getCreatedAt());
    acct.setUpdatedAt(entity.getUpdatedAt());
    return acct;
  }

  private List<String> parseStringList(String json) {
    if (json == null || json.isBlank()) return new ArrayList<>();
    try {
      return JsonUtils.fromJson(json, new TypeReference<List<String>>() {});
    } catch (Exception e) {
      return new ArrayList<>();
    }
  }

  private Map<String, Double> parseBalances(String json) {
    if (json == null || json.isBlank()) return new LinkedHashMap<>();
    try {
      return JsonUtils.fromJson(json, new TypeReference<Map<String, Double>>() {});
    } catch (Exception e) {
      return new LinkedHashMap<>();
    }
  }

  private ExecutionIntentEntity toEntity(ExecutionIntent intent) {
    ExecutionIntentEntity entity = new ExecutionIntentEntity();
    entity.setId(intent.getId());
    entity.setUserId(intent.getUserId());
    entity.setOrgId(tenantContext.getOrgId());
    entity.setProposalId(intent.getProposalId());
    entity.setProviderId(intent.getProviderId());
    entity.setProviderName(intent.getProviderName());
    entity.setRegion(intent.getRegion());
    entity.setAssetClass(intent.getAssetClass());
    entity.setStatus(intent.getStatus());
    entity.setCreatedAt(intent.getCreatedAt());
    entity.setNote(intent.getNote());
    entity.setOrdersJson(JsonUtils.toJson(intent.getOrders()));
    entity.setBrokerOrderIdsJson(JsonUtils.toJson(intent.getBrokerOrderIds()));
    return entity;
  }

  private ExecutionIntent toDto(ExecutionIntentEntity entity) {
    ExecutionIntent intent = new ExecutionIntent();
    intent.setId(entity.getId());
    intent.setUserId(entity.getUserId());
    intent.setProposalId(entity.getProposalId());
    intent.setProviderId(entity.getProviderId());
    intent.setProviderName(entity.getProviderName());
    intent.setRegion(entity.getRegion());
    intent.setAssetClass(entity.getAssetClass());
    intent.setStatus(entity.getStatus());
    intent.setCreatedAt(entity.getCreatedAt());
    intent.setNote(entity.getNote());
    intent.setOrders(JsonUtils.fromJson(entity.getOrdersJson(), new TypeReference<List<ExecutionOrder>>() {}));
    if (entity.getBrokerOrderIdsJson() != null && !entity.getBrokerOrderIdsJson().isBlank()) {
      intent.setBrokerOrderIds(JsonUtils.fromJson(entity.getBrokerOrderIdsJson(), new TypeReference<List<String>>() {}));
    }
    return intent;
  }

  private void applyIntentUpdate(ExecutionIntentEntity entity, ExecutionIntent intent) {
    entity.setStatus(intent.getStatus());
    entity.setNote(intent.getNote());
    entity.setOrdersJson(JsonUtils.toJson(intent.getOrders()));
    entity.setBrokerOrderIdsJson(JsonUtils.toJson(intent.getBrokerOrderIds()));
  }

  private ExecutionStatus deriveStatus(List<BrokerOrder> orders) {
    if (orders == null || orders.isEmpty()) {
      return ExecutionStatus.SUBMITTED;
    }
    boolean anyRejected = orders.stream().anyMatch(o -> o.getStatus() == BrokerOrderStatus.REJECTED);
    if (anyRejected) {
      return ExecutionStatus.FAILED;
    }
    long filled = orders.stream().filter(o -> o.getStatus() == BrokerOrderStatus.FILLED).count();
    boolean partial = orders.stream().anyMatch(o -> o.getStatus() == BrokerOrderStatus.PARTIALLY_FILLED);
    if (filled == orders.size()) {
      return ExecutionStatus.FILLED;
    }
    if (filled > 0 || partial) {
      return ExecutionStatus.PARTIALLY_FILLED;
    }
    return ExecutionStatus.SUBMITTED;
  }
}
