package com.alphamath.portfolio.application.broker;

import com.alphamath.portfolio.application.audit.AuditService;
import com.alphamath.portfolio.application.compliance.ComplianceService;
import com.alphamath.portfolio.application.marketdata.LatestQuotesResult;
import com.alphamath.portfolio.application.marketdata.MarketDataService;
import com.alphamath.portfolio.application.policy.ProviderPolicyService;
import com.alphamath.portfolio.domain.broker.BrokerConnection;
import com.alphamath.portfolio.domain.broker.BrokerConnectionStatus;
import com.alphamath.portfolio.domain.broker.BrokerDefinition;
import com.alphamath.portfolio.domain.broker.BrokerOrder;
import com.alphamath.portfolio.domain.broker.BrokerOrderLeg;
import com.alphamath.portfolio.domain.broker.BrokerOrderLegRequest;
import com.alphamath.portfolio.domain.broker.BrokerOrderPreview;
import com.alphamath.portfolio.domain.broker.BrokerOrderRequest;
import com.alphamath.portfolio.domain.broker.BrokerOrderReview;
import com.alphamath.portfolio.domain.broker.BrokerPosition;
import com.alphamath.portfolio.domain.marketdata.MarketQuote;
import com.alphamath.portfolio.domain.execution.BrokerAccount;
import com.alphamath.portfolio.domain.execution.OrderType;
import com.alphamath.portfolio.domain.trade.AiRecommendation;
import com.alphamath.portfolio.domain.trade.CheckStatus;
import com.alphamath.portfolio.domain.trade.PolicyCheck;
import com.alphamath.portfolio.domain.trade.TradeSide;
import com.alphamath.portfolio.application.trade.TradingPolicyProperties;
import com.alphamath.portfolio.infrastructure.ai.AiForecastService;
import com.alphamath.portfolio.infrastructure.persistence.BrokerAccountEntity;
import com.alphamath.portfolio.infrastructure.persistence.BrokerAccountRepository;
import com.alphamath.portfolio.infrastructure.persistence.BrokerConnectionEntity;
import com.alphamath.portfolio.infrastructure.persistence.BrokerConnectionRepository;
import com.alphamath.portfolio.infrastructure.persistence.BrokerOrderEntity;
import com.alphamath.portfolio.infrastructure.persistence.BrokerOrderLegEntity;
import com.alphamath.portfolio.infrastructure.persistence.BrokerOrderLegRepository;
import com.alphamath.portfolio.infrastructure.persistence.BrokerOrderRepository;
import com.alphamath.portfolio.infrastructure.persistence.BrokerPositionEntity;
import com.alphamath.portfolio.infrastructure.persistence.BrokerPositionRepository;
import com.alphamath.portfolio.infrastructure.persistence.JsonUtils;
import com.alphamath.portfolio.security.TenantContext;
import com.alphamath.portfolio.application.execution.ExecutionApprovalGuard;
import com.alphamath.portfolio.infrastructure.persistence.ExecutionIntentRepository;
import com.alphamath.portfolio.domain.execution.ExecutionStatus;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class BrokerIntegrationService {
  private final BrokerCatalog catalog;
  private final List<BrokerAdapter> adapters;
  private final BrokerConnectionRepository connections;
  private final BrokerAccountRepository accounts;
  private final BrokerPositionRepository positions;
  private final BrokerOrderRepository orders;
  private final BrokerOrderLegRepository orderLegs;
  private final ComplianceService compliance;
  private final MarketDataService marketData;
  private final AiForecastService aiForecast;
  private final TradingPolicyProperties tradingPolicy;
  private final AuditService audit;
  private final ProviderPolicyService providerPolicy;
  private final TenantContext tenantContext;
  private final double feeBps;
  private final ExecutionApprovalGuard approval;
  private final ExecutionIntentRepository intents;

  public BrokerIntegrationService(BrokerCatalog catalog,
                                  List<BrokerAdapter> adapters,
                                  BrokerConnectionRepository connections,
                                  BrokerAccountRepository accounts,
                                  BrokerPositionRepository positions,
                                  BrokerOrderRepository orders,
                                  BrokerOrderLegRepository orderLegs,
                                  ComplianceService compliance,
                                  MarketDataService marketData,
                                  AiForecastService aiForecast,
                                  TradingPolicyProperties tradingPolicy,
                                  AuditService audit,
                                  ProviderPolicyService providerPolicy,
                                  TenantContext tenantContext,
                                  ExecutionApprovalGuard approval,
                                  ExecutionIntentRepository intents,
                                  @Value("${alphamath.platform.feeBps:50}") double feeBps) {
    this.catalog = catalog;
    this.adapters = adapters;
    this.connections = connections;
    this.accounts = accounts;
    this.positions = positions;
    this.orders = orders;
    this.orderLegs = orderLegs;
    this.compliance = compliance;
    this.marketData = marketData;
    this.aiForecast = aiForecast;
    this.tradingPolicy = tradingPolicy;
    this.audit = audit;
    this.providerPolicy = providerPolicy;
    this.tenantContext = tenantContext;
    this.approval = approval;
    this.intents = intents;
    this.feeBps = Math.max(0.0, feeBps);
  }

  public List<BrokerDefinition> listBrokers() {
    return catalog.list();
  }

  public BrokerConnection connect(String userId, String brokerId, String label, Map<String, Object> metadata) {
    BrokerDefinition def = catalog.find(brokerId);
    if (def == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Broker not supported");
    }

    BrokerConnection connection = new BrokerConnection();
    connection.setId(UUID.randomUUID().toString());
    connection.setUserId(userId);
    connection.setBrokerId(def.getId());
    connection.setStatus(BrokerConnectionStatus.CONNECTED);
    connection.setLabel(label);
    connection.setMetadata(metadata == null ? new LinkedHashMap<>() : metadata);
    connection.setCreatedAt(Instant.now());
    connection.setUpdatedAt(Instant.now());

    connections.save(toEntity(connection));
    audit.record(userId, userId, "BROKER_CONNECTION_CREATED", "portfolio_broker_connection", connection.getId(),
        Map.of("brokerId", def.getId()));
    return connection;
  }

  @Transactional(readOnly = true)
  public List<BrokerConnection> listConnections(String userId) {
    List<BrokerConnection> out = new ArrayList<>();
    String orgId = tenantContext.getOrgId();
    List<BrokerConnectionEntity> rows = orgId == null
        ? connections.findByUserIdOrderByCreatedAtDesc(userId)
        : connections.findByUserIdAndOrgIdOrderByCreatedAtDesc(userId, orgId);
    for (BrokerConnectionEntity entity : rows) {
      if (tenantContext.owns(userId, entity.getUserId(), entity.getOrgId())) out.add(toDto(entity));
    }
    return out;
  }

  @Transactional
  public BrokerSyncResult syncConnection(String userId, String connectionId) {
    String orgId = tenantContext.getOrgId();
    BrokerConnectionEntity entity = orgId == null
        ? connections.findByIdAndUserId(connectionId, userId)
        : connections.findByIdAndUserIdAndOrgId(connectionId, userId, orgId);
    if (entity == null || !tenantContext.owns(userId, entity.getUserId(), entity.getOrgId())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Connection not found");
    }
    BrokerConnection connection = toDto(entity);

    BrokerAdapter adapter = adapterFor(connection.getBrokerId());
    BrokerSnapshot snapshot = adapter.sync(connection);
    if (snapshot == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Broker sync returned no data");
    }

    Map<String, String> accountIdMap = new HashMap<>();
    List<BrokerAccount> accountDtos = snapshot.accounts() == null ? List.of() : snapshot.accounts();
    for (BrokerAccount acct : accountDtos) {
      String internalId = upsertAccount(userId, connection, acct);
      accountIdMap.put(acct.getId(), internalId);
      if (acct.getExternalAccountId() != null) {
        accountIdMap.put(acct.getExternalAccountId(), internalId);
      }
    }

    List<String> accountIds = new ArrayList<>(accountIdMap.values());
    for (String accountId : accountIds) {
      positions.deleteByUserIdAndOrgIdAndBrokerAccountId(userId, orgId, accountId);
      List<BrokerOrderEntity> existingOrders = orgId == null
          ? orders.findByUserIdAndBrokerAccountIdOrderByUpdatedAtDesc(userId, accountId)
          : orders.findByUserIdAndOrgIdAndBrokerAccountIdOrderByUpdatedAtDesc(userId, orgId, accountId);
      existingOrders = existingOrders.stream().filter(row -> tenantContext.owns(userId, row.getUserId(), row.getOrgId())).toList();
      for (BrokerOrderEntity row : existingOrders) {
        orderLegs.deleteByOrderId(row.getId());
      }
      orders.deleteAll(existingOrders);
    }

    int positionCount = 0;
    for (BrokerPosition pos : snapshot.positions() == null ? List.<BrokerPosition>of() : snapshot.positions()) {
      String resolvedAccountId = resolveAccountId(accountIdMap, accountDtos, pos.getBrokerAccountId());
      if (resolvedAccountId == null) continue;
      pos.setBrokerAccountId(resolvedAccountId);
      pos.setUserId(userId);
      positions.save(toEntity(pos));
      positionCount++;
    }

    int orderCount = 0;
    for (BrokerOrder order : snapshot.orders() == null ? List.<BrokerOrder>of() : snapshot.orders()) {
      String resolvedAccountId = resolveAccountId(accountIdMap, accountDtos, order.getBrokerAccountId());
      if (resolvedAccountId == null) continue;
      order.setBrokerAccountId(resolvedAccountId);
      order.setUserId(userId);
      BrokerOrderEntity orderEntity = toEntity(order);
      orders.save(orderEntity);
      orderCount++;

      for (BrokerOrderLeg leg : order.getLegs()) {
        leg.setOrderId(orderEntity.getId());
        orderLegs.save(toEntity(leg));
      }
    }

    entity.setLastSyncedAt(Instant.now());
    entity.setUpdatedAt(Instant.now());
    connections.save(entity);

    audit.record(userId, userId, "BROKER_SYNC", "portfolio_broker_connection", entity.getId(),
        Map.of("accounts", accountDtos.size(), "positions", positionCount, "orders", orderCount));
    return new BrokerSyncResult(connectionId, accountDtos.size(), positionCount, orderCount);
  }

  @Transactional(readOnly = true)
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

  @Transactional(readOnly = true)
  public List<BrokerPosition> listPositions(String userId, String accountId) {
    BrokerAccountEntity account = accounts.findById(accountId).orElse(null);
    String orgId = tenantContext.getOrgId();
    if (account == null || !tenantContext.owns(userId, account.getUserId(), account.getOrgId())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
    }
    List<BrokerPosition> out = new ArrayList<>();
    List<BrokerPositionEntity> rows = orgId == null
        ? positions.findByUserIdAndBrokerAccountIdOrderByUpdatedAtDesc(userId, accountId)
        : positions.findByUserIdAndOrgIdAndBrokerAccountIdOrderByUpdatedAtDesc(userId, orgId, accountId);
    for (BrokerPositionEntity entity : rows) {
      if (tenantContext.owns(userId, entity.getUserId(), entity.getOrgId())) out.add(toDto(entity));
    }
    return out;
  }

  @Transactional(readOnly = true)
  public List<BrokerOrder> listOrders(String userId, String accountId) {
    BrokerAccountEntity account = accounts.findById(accountId).orElse(null);
    String orgId = tenantContext.getOrgId();
    if (account == null || !tenantContext.owns(userId, account.getUserId(), account.getOrgId())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
    }
    List<BrokerOrder> out = new ArrayList<>();
    List<BrokerOrderEntity> rows = orgId == null
        ? orders.findByUserIdAndBrokerAccountIdOrderByUpdatedAtDesc(userId, accountId)
        : orders.findByUserIdAndOrgIdAndBrokerAccountIdOrderByUpdatedAtDesc(userId, orgId, accountId);
    for (BrokerOrderEntity entity : rows) {
      if (!tenantContext.owns(userId, entity.getUserId(), entity.getOrgId())) continue;
      List<BrokerOrderLegEntity> legs = orderLegs.findByOrderId(entity.getId());
      out.add(toDto(entity, legs));
    }
    return out;
  }

  public BrokerOrder placeOrder(String userId, String accountId, BrokerOrderRequest request) {
    BrokerAccountEntity account = accounts.findById(accountId).orElse(null);
    if (account == null || !tenantContext.owns(userId, account.getUserId(), account.getOrgId())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
    }
    throw new ResponseStatusException(HttpStatus.CONFLICT, "Orders must be submitted through an approved execution intent");
  }

  @Transactional
  public BrokerOrder placeApprovedOrder(String userId, String accountId, String intentId, int orderIndex) {
    var intent = intents.findById(intentId).orElse(null);
    var proposal = approval.requireBoundIntent(userId, intent);
    if (intent.getStatus() != ExecutionStatus.SUBMITTING || !accountId.equals(intent.getBrokerAccountId())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "A claimed execution intent is required");
    }
    BrokerAccountEntity account = accounts.findById(accountId).orElse(null);
    String orgId = tenantContext.getOrgId();
    if (account == null || !tenantContext.owns(userId, account.getUserId(), account.getOrgId())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
    }
    if (account.getStatus() != com.alphamath.portfolio.domain.execution.BrokerAccountStatus.LINKED
        || !intent.getProviderId().equals(account.getProviderId()) || account.getRegion() != intent.getRegion()
        || !toDto(account).getAssetClasses().contains(intent.getAssetClass())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Broker account no longer matches approved execution");
    }
    var approvedOrders = approval.executionOrders(proposal);
    if (orderIndex < 0 || orderIndex >= approvedOrders.size()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid approved order index");
    }
    var approvedOrder = approvedOrders.get(orderIndex);
    BrokerOrderRequest request = new BrokerOrderRequest();
    request.setClientOrderId("intent-" + intentId + "-" + orderIndex);
    request.setSymbol(approvedOrder.getSymbol());
    request.setSide(approvedOrder.getSide());
    request.setQuantity(approvedOrder.getQuantity());
    request.setAssetClass(intent.getAssetClass());
    request.setOrderType(approvedOrder.getOrderType());
    request.setTimeInForce(approvedOrder.getTimeInForce());
    request.setLimitPrice(approvedOrder.getLimitPrice());
    request.setCurrency(account.getBaseCurrency());
    compliance.requireLiveEligibility(userId);
    providerPolicy.enforceAssetClass(account.getProviderId(), request.getAssetClass());
    String clientOrderId = request.getClientOrderId();
    if (clientOrderId != null) {
      clientOrderId = clientOrderId.trim();
    }
    if (clientOrderId != null && !clientOrderId.isBlank()) {
      BrokerOrderEntity existing = orgId == null
          ? orders.findByUserIdAndBrokerAccountIdAndClientOrderId(userId, accountId, clientOrderId)
          : orders.findByUserIdAndOrgIdAndBrokerAccountIdAndClientOrderId(userId, orgId, accountId, clientOrderId);
      if (existing != null && tenantContext.owns(userId, existing.getUserId(), existing.getOrgId())) {
        List<BrokerOrderLegEntity> legs = orderLegs.findByOrderId(existing.getId());
        return toDto(existing, legs);
      }
    }
    BrokerConnection connection = resolveConnection(userId, account);
    BrokerAdapter adapter = adapterFor(account.getProviderId());

    BrokerOrder order = adapter.placeOrder(connection, request);
    if (order.getClientOrderId() == null || order.getClientOrderId().isBlank()) {
      order.setClientOrderId(clientOrderId);
    }
    order.setBrokerAccountId(accountId);
    order.setUserId(userId);

    BrokerOrderEntity entity = toEntity(order);
    try {
      orders.save(entity);
    } catch (DataIntegrityViolationException ex) {
      if (clientOrderId != null && !clientOrderId.isBlank()) {
        BrokerOrderEntity existing = orgId == null
            ? orders.findByUserIdAndBrokerAccountIdAndClientOrderId(userId, accountId, clientOrderId)
            : orders.findByUserIdAndOrgIdAndBrokerAccountIdAndClientOrderId(userId, orgId, accountId, clientOrderId);
        if (existing != null && tenantContext.owns(userId, existing.getUserId(), existing.getOrgId())) {
          List<BrokerOrderLegEntity> legs = orderLegs.findByOrderId(existing.getId());
          return toDto(existing, legs);
        }
      }
      throw ex;
    }

    for (BrokerOrderLeg leg : order.getLegs()) {
      leg.setOrderId(entity.getId());
      orderLegs.save(toEntity(leg));
    }

    audit.record(userId, userId, "BROKER_ORDER_SUBMITTED", "portfolio_broker_order", entity.getId(),
        Map.of("accountId", accountId, "orderType", order.getOrderType() == null ? "UNKNOWN" : order.getOrderType().name()));
    return order;
  }

  @Transactional
  public BrokerOrderPreview previewOrder(String userId, String accountId, BrokerOrderRequest request) {
    BrokerAccountEntity account = accounts.findById(accountId).orElse(null);
    String orgId = tenantContext.getOrgId();
    if (account == null || !tenantContext.owns(userId, account.getUserId(), account.getOrgId())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
    }
    providerPolicy.enforceAssetClass(account.getProviderId(), request.getAssetClass());
    BrokerConnection connection = resolveConnection(userId, account);
    BrokerAdapter adapter = adapterFor(account.getProviderId());
    BrokerOrderPreview preview = adapter.previewOrder(connection, request);
    if (preview.getBrokerAccountId() == null) {
      preview.setBrokerAccountId(accountId);
    }
    if (preview.getBrokerId() == null) {
      preview.setBrokerId(account.getProviderId());
    }
    if (preview.getCurrency() == null || preview.getCurrency().isBlank()) {
      preview.setCurrency(account.getBaseCurrency() == null ? "USD" : account.getBaseCurrency());
    }
    if (preview.getCreatedAt() == null) {
      preview.setCreatedAt(Instant.now());
    }
    appendFractionalWarnings(request, preview.getWarnings());
    OrderNotionalSummary notional = resolveOrderNotional(request, preview, preview.getWarnings());
    if (notional.multiLeg || preview.getEstimatedNotional() == null || preview.getEstimatedNotional() <= 0.0) {
      preview.setEstimatedNotional(notional.grossNotional);
    }
    if (preview.getEstimatedFees() == null || preview.getEstimatedFees() < 0.0) {
      preview.setEstimatedFees(estimateFees(account.getProviderId(), request.getAssetClass(),
          request.getQuantity(), notional.grossNotional));
    }
    if (preview.getEstimatedTotal() == null || preview.getEstimatedTotal() <= 0.0) {
      preview.setEstimatedTotal(preview.getEstimatedNotional() + preview.getEstimatedFees());
    }
    if (!notional.legs.isEmpty()) {
      preview.getMetadata().put("legs", legsToMaps(notional.legs));
      preview.getMetadata().put("netNotional", round(notional.netNotional, 2));
      if (preview.getSymbol() == null || preview.getSymbol().isBlank()) {
        preview.setSymbol(notional.legs.get(0).symbol);
      }
    }
    audit.record(userId, userId, "BROKER_ORDER_PREVIEW", "portfolio_broker_account", accountId,
        Map.of("symbol", request.getSymbol(), "quantity", request.getQuantity()));
    return preview;
  }

  @Transactional
  public BrokerOrderReview reviewOrder(String userId,
                                       String accountId,
                                       BrokerOrderRequest request,
                                       Integer aiHorizon,
                                       Integer lookback,
                                       boolean includeCompliance) {
    BrokerOrderPreview preview = previewOrder(userId, accountId, request);
    BrokerOrderReview review = new BrokerOrderReview();
    review.setPreview(preview);
    review.setCreatedAt(Instant.now());

    BrokerAccountEntity accountEntity = accounts.findById(accountId).orElse(null);
    BrokerAccount account = accountEntity == null ? null : toDto(accountEntity);

    String symbol = resolvePrimarySymbol(request);
    Double price = resolvePrice(preview, request, symbol);
    if (price != null && (preview.getPrice() == null || preview.getPrice() <= 0.0)) {
      preview.setPrice(price);
    }

    OrderNotionalSummary notionalSummary = resolveOrderNotional(request, preview, review.getWarnings());
    double notional = preview.getEstimatedNotional() == null || preview.getEstimatedNotional() <= 0.0
        ? notionalSummary.grossNotional
        : preview.getEstimatedNotional();
    if (notional <= 0.0) {
      notional = estimateNotional(request, price);
    }
    if (preview.getEstimatedNotional() == null || preview.getEstimatedNotional() <= 0.0
        || notionalSummary.multiLeg) {
      preview.setEstimatedNotional(notional);
    }
    Double fees = preview.getEstimatedFees();
    if (fees == null || fees < 0.0) {
      fees = estimateFees(account.getProviderId(), request.getAssetClass(), request.getQuantity(), notional);
      preview.setEstimatedFees(fees);
    }
    if (preview.getEstimatedTotal() == null || preview.getEstimatedTotal() <= 0.0) {
      preview.setEstimatedTotal(notional + fees);
    }

    List<BrokerPosition> positions = listPositions(userId, accountId);
    Double equity = buildCashImpact(review, account, request, notional, fees, positions,
        notionalSummary.multiLeg ? notionalSummary.netNotional : null,
        notionalSummary.legs);
    buildPositionImpact(review, positions, request, notionalSummary.legs, equity);
    buildPolicyChecks(review, userId, request, symbol, notional, includeCompliance);

    if (notionalSummary.multiLeg) {
      review.getWarnings().add("Multi-leg AI review uses the primary symbol only");
    }
    List<Double> returns = loadReturns(symbol, lookback);
    applyRiskWarnings(review, returns, aiHorizon);
    applyPriceDeviationWarnings(review, notionalSummary.legs);
    review.setAi(buildAiReview(request, symbol, price, notional, aiHorizon, returns));
    review.setDisclaimer("Manual trading stays under your control. AI guidance is informational only.");
    audit.record(userId, userId, "BROKER_ORDER_REVIEW", "portfolio_broker_account", accountId,
        Map.of("symbol", request.getSymbol(), "quantity", request.getQuantity()));
    return review;
  }

  @Transactional
  public BrokerOrder cancelOrder(String userId, String accountId, String orderId) {
    BrokerAccountEntity account = accounts.findById(accountId).orElse(null);
    String orgId = tenantContext.getOrgId();
    if (account == null || !tenantContext.owns(userId, account.getUserId(), account.getOrgId())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
    }
    BrokerOrderEntity entity = orgId == null
        ? orders.findByIdAndUserId(orderId, userId)
        : orders.findByIdAndUserIdAndOrgId(orderId, userId, orgId);
    if (entity == null || !tenantContext.owns(userId, entity.getUserId(), entity.getOrgId())
        || !accountId.equals(entity.getBrokerAccountId())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
    }
    List<BrokerOrderLegEntity> legs = orderLegs.findByOrderId(entity.getId());
    BrokerOrder order = toDto(entity, legs);
    BrokerConnection connection = resolveConnection(userId, account);
    BrokerAdapter adapter = adapterFor(account.getProviderId());
    BrokerOrder updated = adapter.cancelOrder(connection, order);
    applyOrderUpdate(entity, updated);
    orders.save(entity);
    audit.record(userId, userId, "BROKER_ORDER_CANCELED", "portfolio_broker_order", entity.getId(),
        Map.of("accountId", accountId));
    return updated;
  }

  @Transactional
  public BrokerOrder refreshOrder(String userId, String accountId, String orderId) {
    BrokerAccountEntity account = accounts.findById(accountId).orElse(null);
    String orgId = tenantContext.getOrgId();
    if (account == null || !tenantContext.owns(userId, account.getUserId(), account.getOrgId())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
    }
    BrokerOrderEntity entity = orgId == null
        ? orders.findByIdAndUserId(orderId, userId)
        : orders.findByIdAndUserIdAndOrgId(orderId, userId, orgId);
    if (entity == null || !tenantContext.owns(userId, entity.getUserId(), entity.getOrgId())
        || !accountId.equals(entity.getBrokerAccountId())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
    }
    List<BrokerOrderLegEntity> legs = orderLegs.findByOrderId(entity.getId());
    BrokerOrder order = toDto(entity, legs);
    BrokerConnection connection = resolveConnection(userId, account);
    BrokerAdapter adapter = adapterFor(account.getProviderId());
    BrokerOrder updated = adapter.refreshOrder(connection, order);
    applyOrderUpdate(entity, updated);
    orders.save(entity);
    audit.record(userId, userId, "BROKER_ORDER_REFRESH", "portfolio_broker_order", entity.getId(),
        Map.of("accountId", accountId, "status", updated.getStatus() == null ? "UNKNOWN" : updated.getStatus().name()));
    return updated;
  }

  private BrokerAdapter adapterFor(String brokerId) {
    for (BrokerAdapter adapter : adapters) {
      if (adapter.supports(brokerId)) return adapter;
    }
    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No adapter for broker");
  }

  private BrokerConnection resolveConnection(String userId, BrokerAccountEntity account) {
    if (account.getBrokerConnectionId() == null || account.getBrokerConnectionId().isBlank()) {
      throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "Broker connection must be linked");
    }
    String orgId = tenantContext.getOrgId();
    BrokerConnectionEntity entity = orgId == null
        ? connections.findByIdAndUserId(account.getBrokerConnectionId(), userId)
        : connections.findByIdAndUserIdAndOrgId(account.getBrokerConnectionId(), userId, orgId);
    if (entity == null || !tenantContext.owns(userId, entity.getUserId(), entity.getOrgId())
        || !account.getProviderId().equals(entity.getBrokerId())
        || entity.getStatus() != BrokerConnectionStatus.CONNECTED) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Active broker connection not found");
    }
    return toDto(entity);
  }

  private String upsertAccount(String userId, BrokerConnection connection, BrokerAccount acct) {
    BrokerAccountEntity existing = null;
    if (acct.getExternalAccountId() != null && !acct.getExternalAccountId().isBlank()) {
      String orgId = tenantContext.getOrgId();
      existing = orgId == null
          ? accounts.findByUserIdAndExternalAccountId(userId, acct.getExternalAccountId())
          : accounts.findByUserIdAndOrgIdAndExternalAccountId(userId, orgId, acct.getExternalAccountId());
    }
    if (existing != null && !tenantContext.owns(userId, existing.getUserId(), existing.getOrgId())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
    }
    BrokerAccountEntity entity = existing == null ? new BrokerAccountEntity() : existing;
    String id = existing == null ? UUID.randomUUID().toString() : entity.getId();

    entity.setId(id);
    entity.setUserId(userId);
    entity.setOrgId(tenantContext.getOrgId());
    entity.setProviderId(connection.getBrokerId());
    entity.setProviderName(acct.getProviderName() == null ? connection.getBrokerId() : acct.getProviderName());
    entity.setBrokerConnectionId(connection.getId());
    entity.setExternalAccountId(acct.getExternalAccountId());
    entity.setAccountNumber(acct.getAccountNumber());
    entity.setBaseCurrency(acct.getBaseCurrency());
    entity.setAccountType(acct.getAccountType());
    entity.setRegion(acct.getRegion());
    entity.setAssetClassesJson(JsonUtils.toJson(acct.getAssetClasses()));
    entity.setPermissionsJson(JsonUtils.toJson(acct.getPermissions()));
    entity.setBalancesJson(JsonUtils.toJson(acct.getBalances()));
    entity.setStatus(acct.getStatus());
    entity.setCreatedAt(existing == null ? Instant.now() : entity.getCreatedAt());
    entity.setUpdatedAt(Instant.now());
    accounts.save(entity);
    return entity.getId();
  }

  private String resolveAccountId(Map<String, String> map, List<BrokerAccount> accounts, String brokerAccountId) {
    if (brokerAccountId != null && map.containsKey(brokerAccountId)) {
      return map.get(brokerAccountId);
    }
    if (!map.isEmpty()) {
      return map.values().iterator().next();
    }
    if (accounts != null && !accounts.isEmpty()) {
      return map.get(accounts.get(0).getId());
    }
    return null;
  }

  private BrokerConnectionEntity toEntity(BrokerConnection conn) {
    BrokerConnectionEntity entity = new BrokerConnectionEntity();
    entity.setId(conn.getId());
    entity.setUserId(conn.getUserId());
    entity.setOrgId(tenantContext.getOrgId());
    entity.setBrokerId(conn.getBrokerId());
    entity.setStatus(conn.getStatus());
    entity.setLabel(conn.getLabel());
    entity.setMetadataJson(JsonUtils.toJson(conn.getMetadata()));
    entity.setCreatedAt(conn.getCreatedAt());
    entity.setUpdatedAt(conn.getUpdatedAt());
    entity.setLastSyncedAt(conn.getLastSyncedAt());
    return entity;
  }

  private BrokerConnection toDto(BrokerConnectionEntity entity) {
    BrokerConnection conn = new BrokerConnection();
    conn.setId(entity.getId());
    conn.setUserId(entity.getUserId());
    conn.setBrokerId(entity.getBrokerId());
    conn.setStatus(entity.getStatus());
    conn.setLabel(entity.getLabel());
    conn.setMetadata(parseMetadata(entity.getMetadataJson()));
    conn.setCreatedAt(entity.getCreatedAt());
    conn.setUpdatedAt(entity.getUpdatedAt());
    conn.setLastSyncedAt(entity.getLastSyncedAt());
    return conn;
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
    acct.setAssetClasses(parseAssetClasses(entity.getAssetClassesJson()));
    acct.setPermissions(parseStringList(entity.getPermissionsJson()));
    acct.setBalances(parseBalances(entity.getBalancesJson()));
    acct.setStatus(entity.getStatus());
    acct.setCreatedAt(entity.getCreatedAt());
    acct.setUpdatedAt(entity.getUpdatedAt());
    return acct;
  }

  private BrokerPositionEntity toEntity(BrokerPosition pos) {
    BrokerPositionEntity entity = new BrokerPositionEntity();
    entity.setId(pos.getId() == null ? UUID.randomUUID().toString() : pos.getId());
    entity.setUserId(pos.getUserId());
    entity.setOrgId(tenantContext.getOrgId());
    entity.setBrokerAccountId(pos.getBrokerAccountId());
    entity.setInstrumentId(pos.getInstrumentId());
    entity.setSymbol(pos.getSymbol());
    entity.setAssetClass(pos.getAssetClass());
    entity.setQuantity(pos.getQuantity());
    entity.setAvgPrice(pos.getAvgPrice());
    entity.setMarketPrice(pos.getMarketPrice());
    entity.setMarketValue(pos.getMarketValue());
    entity.setCostBasis(pos.getCostBasis());
    entity.setUnrealizedPnl(pos.getUnrealizedPnl());
    entity.setCurrency(pos.getCurrency());
    entity.setMetadataJson(JsonUtils.toJson(pos.getMetadata()));
    entity.setUpdatedAt(pos.getUpdatedAt() == null ? Instant.now() : pos.getUpdatedAt());
    return entity;
  }

  private BrokerPosition toDto(BrokerPositionEntity entity) {
    BrokerPosition pos = new BrokerPosition();
    pos.setId(entity.getId());
    pos.setUserId(entity.getUserId());
    pos.setBrokerAccountId(entity.getBrokerAccountId());
    pos.setInstrumentId(entity.getInstrumentId());
    pos.setSymbol(entity.getSymbol());
    pos.setAssetClass(entity.getAssetClass());
    pos.setQuantity(entity.getQuantity());
    pos.setAvgPrice(entity.getAvgPrice());
    pos.setMarketPrice(entity.getMarketPrice());
    pos.setMarketValue(entity.getMarketValue());
    pos.setCostBasis(entity.getCostBasis());
    pos.setUnrealizedPnl(entity.getUnrealizedPnl());
    pos.setCurrency(entity.getCurrency());
    pos.setMetadata(parseMetadata(entity.getMetadataJson()));
    pos.setUpdatedAt(entity.getUpdatedAt());
    return pos;
  }

  private BrokerOrderEntity toEntity(BrokerOrder order) {
    BrokerOrderEntity entity = new BrokerOrderEntity();
    entity.setId(order.getId() == null ? UUID.randomUUID().toString() : order.getId());
    entity.setUserId(order.getUserId());
    entity.setOrgId(tenantContext.getOrgId());
    entity.setBrokerAccountId(order.getBrokerAccountId());
    entity.setExternalOrderId(order.getExternalOrderId());
    entity.setClientOrderId(order.getClientOrderId());
    entity.setStatus(order.getStatus());
    entity.setOrderType(order.getOrderType());
    entity.setSide(order.getSide());
    entity.setTimeInForce(order.getTimeInForce());
    entity.setSubmittedAt(order.getSubmittedAt());
    entity.setUpdatedAt(order.getUpdatedAt());
    entity.setFilledAt(order.getFilledAt());
    entity.setTotalQuantity(order.getTotalQuantity());
    entity.setFilledQuantity(order.getFilledQuantity());
    entity.setAvgPrice(order.getAvgPrice());
    entity.setCurrency(order.getCurrency());
    entity.setMetadataJson(JsonUtils.toJson(order.getMetadata()));
    return entity;
  }

  private void applyOrderUpdate(BrokerOrderEntity entity, BrokerOrder updated) {
    if (updated.getExternalOrderId() != null) {
      entity.setExternalOrderId(updated.getExternalOrderId());
    }
    if (updated.getClientOrderId() != null && !updated.getClientOrderId().isBlank()) {
      entity.setClientOrderId(updated.getClientOrderId());
    }
    if (updated.getStatus() != null) {
      entity.setStatus(updated.getStatus());
    }
    if (updated.getOrderType() != null) {
      entity.setOrderType(updated.getOrderType());
    }
    if (updated.getSide() != null) {
      entity.setSide(updated.getSide());
    }
    if (updated.getTimeInForce() != null) {
      entity.setTimeInForce(updated.getTimeInForce());
    }
    if (updated.getSubmittedAt() != null) {
      entity.setSubmittedAt(updated.getSubmittedAt());
    }
    entity.setUpdatedAt(updated.getUpdatedAt() == null ? Instant.now() : updated.getUpdatedAt());
    if (updated.getFilledAt() != null) {
      entity.setFilledAt(updated.getFilledAt());
    }
    if (updated.getTotalQuantity() != null) {
      entity.setTotalQuantity(updated.getTotalQuantity());
    }
    if (updated.getFilledQuantity() != null) {
      entity.setFilledQuantity(updated.getFilledQuantity());
    }
    if (updated.getAvgPrice() != null) {
      entity.setAvgPrice(updated.getAvgPrice());
    }
    if (updated.getCurrency() != null) {
      entity.setCurrency(updated.getCurrency());
    }
    if (updated.getMetadata() != null) {
      entity.setMetadataJson(JsonUtils.toJson(updated.getMetadata()));
    }
  }

  private BrokerOrder toDto(BrokerOrderEntity entity, List<BrokerOrderLegEntity> legs) {
    BrokerOrder order = new BrokerOrder();
    order.setId(entity.getId());
    order.setUserId(entity.getUserId());
    order.setBrokerAccountId(entity.getBrokerAccountId());
    order.setExternalOrderId(entity.getExternalOrderId());
    order.setClientOrderId(entity.getClientOrderId());
    order.setStatus(entity.getStatus());
    order.setOrderType(entity.getOrderType());
    order.setSide(entity.getSide());
    order.setTimeInForce(entity.getTimeInForce());
    order.setSubmittedAt(entity.getSubmittedAt());
    order.setUpdatedAt(entity.getUpdatedAt());
    order.setFilledAt(entity.getFilledAt());
    order.setTotalQuantity(entity.getTotalQuantity());
    order.setFilledQuantity(entity.getFilledQuantity());
    order.setAvgPrice(entity.getAvgPrice());
    order.setCurrency(entity.getCurrency());
    order.setMetadata(parseMetadata(entity.getMetadataJson()));
    List<BrokerOrderLeg> outLegs = new ArrayList<>();
    if (legs != null) {
      for (BrokerOrderLegEntity leg : legs) {
        outLegs.add(toDto(leg));
      }
    }
    order.setLegs(outLegs);
    return order;
  }

  private Double buildCashImpact(BrokerOrderReview review,
                                 BrokerAccount account,
                                 BrokerOrderRequest request,
                                 double notional,
                                 double fees,
                                 List<BrokerPosition> positions,
                                 Double netNotional,
                                 List<LegEstimate> legs) {
    Map<String, Object> impact = review.getCashImpact();
    String currency = request.getCurrency();
    if ((currency == null || currency.isBlank()) && account != null) {
      currency = account.getBaseCurrency();
    }
    if (currency == null || currency.isBlank()) {
      currency = "USD";
    }
    impact.put("currency", currency);
    impact.put("estimatedNotional", round(notional, 2));
    impact.put("estimatedFees", round(fees, 2));
    impact.put("estimatedTotal", round(notional + fees, 2));
    if (netNotional != null) {
      impact.put("netNotional", round(netNotional, 2));
    }
    if (legs != null && legs.size() > 1) {
      impact.put("legCount", legs.size());
    }

    Map<String, Double> balances = account == null ? null : account.getBalances();
    Double available = resolveBalance(balances, currency);
    if (available != null) {
      impact.put("available", round(available, 2));
      double cashDelta = netNotional == null
          ? orderCashDelta(request, notional, fees)
          : netNotional - Math.max(0.0, fees);
      impact.put("cashDelta", round(cashDelta, 2));
      impact.put("projectedAvailable", round(available + cashDelta, 2));
      boolean ok = available + cashDelta >= 0.0;
      review.getPolicyChecks().add(policyCheck("Cash balance", ok,
          ok ? "Sufficient cash available" : "Insufficient cash for estimated total"));
    } else {
      review.getWarnings().add("Account cash balance unavailable");
    }

    Double buyingPower = resolveBalanceByKeys(balances,
        List.of("buying_power", "buyingPower", "available_to_trade", "available_to_trade_cash",
            "available_margin", "margin_available"));
    if (buyingPower != null) {
      impact.put("buyingPower", round(buyingPower, 2));
      double cashDelta = netNotional == null
          ? orderCashDelta(request, notional, fees)
          : netNotional - Math.max(0.0, fees);
      if (cashDelta < 0.0) {
        boolean ok = buyingPower + cashDelta >= 0.0;
        review.getPolicyChecks().add(policyCheck("Buying power", ok,
            ok ? "Within buying power" : "Order exceeds buying power"));
      }
    }

    double equity = estimateEquity(available, positions);
    if (equity > 0.0) {
      double orderPct = notional / equity;
      impact.put("estimatedEquity", round(equity, 2));
      impact.put("orderPctEquity", round(orderPct, 4));
      double maxOrderPct = tradingPolicy == null ? 0.0 : tradingPolicy.getMaxSingleOrderPctEquity();
      if (maxOrderPct > 0.0 && orderPct > maxOrderPct) {
        review.getPolicyChecks().add(policyCheck("Order size concentration", CheckStatus.WARN,
            "Order is " + round(orderPct * 100.0, 2) + "% of equity (limit " + round(maxOrderPct * 100.0, 2) + "%)"));
      }
    }
    if (tradingPolicy != null && tradingPolicy.getMinOrderNotional() > 0.0
        && notional > 0.0 && notional < tradingPolicy.getMinOrderNotional()) {
      review.getPolicyChecks().add(policyCheck("Minimum order size", CheckStatus.WARN,
          "Notional below $" + round(tradingPolicy.getMinOrderNotional(), 2)));
    }
    if (tradingPolicy != null) {
      if (tradingPolicy.getMaxSingleOrderNotional() > 0.0 && notional > tradingPolicy.getMaxSingleOrderNotional()) {
        review.getPolicyChecks().add(policyCheck("Order notional cap", CheckStatus.WARN,
            "Notional exceeds $" + round(tradingPolicy.getMaxSingleOrderNotional(), 2)));
      }
      if (tradingPolicy.getMaxGrossNotional() > 0.0 && notional > tradingPolicy.getMaxGrossNotional()) {
        review.getPolicyChecks().add(policyCheck("Gross notional cap", CheckStatus.WARN,
            "Gross notional exceeds $" + round(tradingPolicy.getMaxGrossNotional(), 2)));
      }
      if (netNotional != null && tradingPolicy.getMaxNetNotional() > 0.0
          && Math.abs(netNotional) > tradingPolicy.getMaxNetNotional()) {
        review.getPolicyChecks().add(policyCheck("Net notional cap", CheckStatus.WARN,
            "Net notional exceeds $" + round(tradingPolicy.getMaxNetNotional(), 2)));
      }
    }
    return equity > 0.0 ? equity : null;
  }

  private void buildPositionImpact(BrokerOrderReview review,
                                   List<BrokerPosition> positions,
                                   BrokerOrderRequest request,
                                   List<LegEstimate> legs,
                                   Double equity) {
    if (legs == null || legs.isEmpty()) {
      return;
    }
    Map<String, Object> impact = review.getPositionImpact();

    Map<String, LegAggregate> aggregates = new LinkedHashMap<>();
    for (LegEstimate leg : legs) {
      if (leg == null || leg.symbol == null || leg.symbol.isBlank()) {
        continue;
      }
      LegAggregate agg = aggregates.computeIfAbsent(leg.symbol.toUpperCase(), key -> new LegAggregate());
      double delta = leg.side == TradeSide.SELL ? -leg.quantity : leg.quantity;
      agg.symbol = leg.symbol.toUpperCase();
      agg.deltaQuantity += delta;
      if (leg.price != null && leg.price > 0.0) {
        agg.price = leg.price;
      }
    }

    List<Map<String, Object>> symbolImpacts = new ArrayList<>();
    for (LegAggregate agg : aggregates.values()) {
      BrokerPosition position = null;
      for (BrokerPosition pos : positions) {
        if (pos.getSymbol() != null && pos.getSymbol().equalsIgnoreCase(agg.symbol)) {
          position = pos;
          break;
        }
      }
      double currentQty = position == null ? 0.0 : position.getQuantity();
      double newQty = currentQty + agg.deltaQuantity;

      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("symbol", agg.symbol);
      entry.put("currentQuantity", round(currentQty, 6));
      entry.put("deltaQuantity", round(agg.deltaQuantity, 6));
      entry.put("projectedQuantity", round(newQty, 6));
      Double newValue = null;
      if (agg.price != null && agg.price > 0.0) {
        newValue = newQty * agg.price;
        entry.put("estimatedMarketValue", round(newValue, 2));
      }
      symbolImpacts.add(entry);

      if (agg.deltaQuantity < 0.0 && Math.abs(agg.deltaQuantity) > currentQty) {
        review.getPolicyChecks().add(policyCheck("Short sell risk", CheckStatus.WARN,
            "Sell quantity exceeds current position for " + agg.symbol));
      }
      if (!request.isAllowFractional() && agg.deltaQuantity % 1.0 != 0.0) {
        review.getWarnings().add("Fractional quantity may not be supported for " + agg.symbol);
      }

      if (tradingPolicy != null && newValue != null && newValue > 0.0 && equity != null && equity > 0.0) {
        double positionPct = newValue / equity;
        double maxPositionPct = tradingPolicy.getMaxSinglePositionPctEquity();
        if (maxPositionPct > 0.0 && positionPct > maxPositionPct) {
          review.getPolicyChecks().add(policyCheck("Position concentration", CheckStatus.WARN,
              agg.symbol + " would be " + round(positionPct * 100.0, 2)
                  + "% of equity (limit " + round(maxPositionPct * 100.0, 2) + "%)"));
        }
      }
    }

    if (symbolImpacts.size() == 1) {
      impact.putAll(symbolImpacts.get(0));
      if (legs.size() > 1) {
        impact.put("legCount", legs.size());
      }
    } else {
      impact.put("symbols", symbolImpacts);
      impact.put("symbolCount", symbolImpacts.size());
      impact.put("legCount", legs.size());
    }
  }

  private void buildPolicyChecks(BrokerOrderReview review,
                                 String userId,
                                 BrokerOrderRequest request,
                                 String symbol,
                                 double notional,
                                 boolean includeCompliance) {
    List<BrokerOrderLegRequest> legs = request.getLegs();
    boolean hasLegs = legs != null && !legs.isEmpty();
    if (hasLegs) {
      int index = 1;
      for (BrokerOrderLegRequest leg : legs) {
        if (leg == null) {
          review.getWarnings().add("Order leg missing at index " + index);
          index++;
          continue;
        }
        if (leg.getSymbol() == null || leg.getSymbol().isBlank()) {
          review.getWarnings().add("Leg symbol missing at index " + index);
        }
        if (leg.getQuantity() <= 0.0) {
          review.getPolicyChecks().add(policyCheck("Leg quantity", false,
              "Quantity must be positive for leg " + index));
        }
        index++;
      }
    } else {
      if (symbol == null || symbol.isBlank()) {
        review.getWarnings().add("Symbol missing; AI review limited");
      }
      if (request.getQuantity() <= 0.0) {
        review.getPolicyChecks().add(policyCheck("Quantity", false, "Quantity must be positive"));
      }
    }
    OrderType orderType = request.getOrderType();
    if (orderType == null) {
      review.getWarnings().add("Order type not specified");
    } else {
      if (hasLegs) {
        int index = 1;
        for (BrokerOrderLegRequest leg : legs) {
          if (leg == null) {
            index++;
            continue;
          }
          if ((orderType == OrderType.LIMIT || orderType == OrderType.STOP_LIMIT)
              && (leg.getLimitPrice() == null || leg.getLimitPrice() <= 0.0)) {
            review.getPolicyChecks().add(policyCheck("Limit price", false,
                "Limit price required for leg " + index + " (" + orderType + ")"));
          }
          if ((orderType == OrderType.STOP || orderType == OrderType.STOP_LIMIT)
              && (leg.getStopPrice() == null || leg.getStopPrice() <= 0.0)) {
            review.getPolicyChecks().add(policyCheck("Stop price", false,
                "Stop price required for leg " + index + " (" + orderType + ")"));
          }
          index++;
        }
      } else {
        if ((orderType == OrderType.LIMIT || orderType == OrderType.STOP_LIMIT)
            && (request.getLimitPrice() == null || request.getLimitPrice() <= 0.0)) {
          review.getPolicyChecks().add(policyCheck("Limit price", false, "Limit price required for " + orderType));
        }
        if ((orderType == OrderType.STOP || orderType == OrderType.STOP_LIMIT)
            && (request.getStopPrice() == null || request.getStopPrice() <= 0.0)) {
          review.getPolicyChecks().add(policyCheck("Stop price", false, "Stop price required for " + orderType));
        }
      }
    }
    if (notional <= 0.0) {
      review.getWarnings().add("Estimated notional unavailable");
    }
    if (includeCompliance) {
      review.getPolicyChecks().addAll(compliance.complianceChecks(userId));
    }
  }

  private AiRecommendation buildAiReview(BrokerOrderRequest request,
                                         String symbol,
                                         Double price,
                                         double notional,
                                         Integer aiHorizon,
                                         List<Double> returns) {
    if (symbol == null || symbol.isBlank()) {
      return null;
    }
    int horizon = aiHorizon == null || aiHorizon <= 0 ? 1 : aiHorizon;

    AiRecommendation ai = new AiRecommendation();
    TradeSide side = request.getSide() == null ? TradeSide.BUY : request.getSide();
    ai.setSummary("AI review for manual order: " + side.name() + " " + symbol.toUpperCase());
    ai.setConfidence(0.5);
    ai.setHorizon(horizon);
    ai.setModel("ai-service");

    if (returns != null && returns.size() >= 30) {
      var forecast = aiForecast.predict(returns, horizon);
      if (forecast != null) {
        ai.setExpectedReturn(forecast.expectedReturn());
        ai.setVolatility(forecast.volatility());
        ai.setPUp(forecast.pUp());
        ai.setConfidence(forecast.confidence());
        ai.setDisclaimer(forecast.disclaimer());
        ai.getReasons().add("AI expected return=" + round(forecast.expectedReturn(), 4));
        ai.getReasons().add("AI volatility=" + round(forecast.volatility(), 4));
        ai.getReasons().add("AI P(up)=" + round(forecast.pUp(), 4));
      }

      var risk = aiForecast.risk(returns, horizon);
      if (risk != null) {
        ai.getReasons().add("AI max drawdown=" + round(risk.maxDrawdown(), 4));
        if (ai.getDisclaimer() == null) {
          ai.setDisclaimer(risk.disclaimer());
        }
      }
    } else {
      ai.getReasons().add("Insufficient return history for AI forecast");
    }

    if (request.getOrderType() != null && request.getOrderType().name().equals("MARKET")) {
      ai.getReasons().add("Market order may increase slippage; consider limit order");
    }
    if (price != null && price > 0.0) {
      ai.getReasons().add("Estimated price=" + round(price, 4));
    }
    ai.getReasons().add("Estimated notional=" + round(notional, 2));
    return ai;
  }

  private List<Double> loadReturns(String symbol, Integer lookback) {
    if (symbol == null || symbol.isBlank()) {
      return List.of();
    }
    int limit = lookback == null || lookback <= 0 ? 120 : lookback;
    try {
      return marketData.returns(symbol, null, null, limit);
    } catch (Exception ignored) {
      return List.of();
    }
  }

  private void applyRiskWarnings(BrokerOrderReview review, List<Double> returns, Integer aiHorizon) {
    if (review == null || tradingPolicy == null || returns == null || returns.size() < 30) {
      return;
    }
    int horizon = aiHorizon == null || aiHorizon <= 0 ? 1 : aiHorizon;
    var risk = aiForecast.risk(returns, horizon);
    if (risk == null) {
      return;
    }
    if (tradingPolicy.getRiskVolatilityWarn() > 0.0 && risk.volatility() > tradingPolicy.getRiskVolatilityWarn()) {
      review.getPolicyChecks().add(policyCheck("Volatility risk", CheckStatus.WARN,
          "AI volatility " + round(risk.volatility(), 4) + " exceeds "
              + round(tradingPolicy.getRiskVolatilityWarn(), 4)));
    }
    if (tradingPolicy.getRiskDrawdownWarn() > 0.0 && risk.maxDrawdown() > tradingPolicy.getRiskDrawdownWarn()) {
      review.getPolicyChecks().add(policyCheck("Drawdown risk", CheckStatus.WARN,
          "AI max drawdown " + round(risk.maxDrawdown(), 4) + " exceeds "
              + round(tradingPolicy.getRiskDrawdownWarn(), 4)));
    }
  }

  private void applyPriceDeviationWarnings(BrokerOrderReview review, List<LegEstimate> legs) {
    if (review == null || tradingPolicy == null || legs == null || legs.isEmpty()) {
      return;
    }
    double threshold = tradingPolicy.getPriceDeviationWarnPct();
    if (threshold <= 0.0) {
      return;
    }
    for (LegEstimate leg : legs) {
      if (leg == null || leg.explicitPrice == null || leg.quotePrice == null) {
        continue;
      }
      if (leg.quotePrice <= 0.0) {
        continue;
      }
      double deviation = Math.abs(leg.explicitPrice - leg.quotePrice) / leg.quotePrice;
      if (deviation >= threshold) {
        String symbol = leg.symbol == null ? "symbol" : leg.symbol;
        review.getWarnings().add("Price deviates " + round(deviation * 100.0, 2) + "% from quote for " + symbol);
      }
    }
  }

  private void appendFractionalWarnings(BrokerOrderRequest request, List<String> warnings) {
    if (request.isAllowFractional()) {
      return;
    }
    List<BrokerOrderLegRequest> legs = request.getLegs();
    if (legs == null || legs.isEmpty()) {
      if (request.getQuantity() % 1.0 != 0.0) {
        warnings.add("Fractional quantity may not be supported");
      }
      return;
    }
    int index = 1;
    for (BrokerOrderLegRequest leg : legs) {
      if (leg != null && leg.getQuantity() % 1.0 != 0.0) {
        warnings.add("Fractional quantity may not be supported for leg " + index);
      }
      index++;
    }
  }

  private OrderNotionalSummary resolveOrderNotional(BrokerOrderRequest request,
                                                    BrokerOrderPreview preview,
                                                    List<String> warnings) {
    List<BrokerOrderLegRequest> legRequests = legsFromRequest(request);
    boolean multiLeg = request.getLegs() != null && !request.getLegs().isEmpty();
    Map<String, Double> latest = fetchLatestPrices(legRequests);
    List<LegEstimate> legs = new ArrayList<>();
    double gross = 0.0;
    double net = 0.0;

    for (BrokerOrderLegRequest leg : legRequests) {
      if (leg == null) continue;
      String symbol = leg.getSymbol();
      if (symbol != null) {
        symbol = symbol.trim().toUpperCase();
      }
      LegPriceContext priceContext = resolveLegPrice(leg, preview, latest, !multiLeg);
      Double explicitPrice = priceContext.explicitPrice;
      Double quotePrice = priceContext.quotePrice;
      Double price = priceContext.effectivePrice;
      double qty = Math.max(0.0, leg.getQuantity());
      if (qty <= 0.0 && warnings != null) {
        warnings.add("Leg quantity missing for " + (symbol == null ? "unknown" : symbol));
      }
      if (price == null || price <= 0.0) {
        if (warnings != null && symbol != null) {
          warnings.add("Price unavailable for " + symbol);
        }
        price = null;
      }
      TradeSide side = leg.getSide() == null ? TradeSide.BUY : leg.getSide();
      com.alphamath.portfolio.domain.execution.AssetClass assetClass =
          leg.getAssetClass() == null ? request.getAssetClass() : leg.getAssetClass();
      double multiplier = resolveContractMultiplier(assetClass, leg.getMetadata(), request.getMetadata());
      double notional = (price == null ? 0.0 : price) * qty * multiplier;
      double signed = side == TradeSide.SELL ? notional : -notional;
      legs.add(new LegEstimate(symbol, side, qty, assetClass, price, explicitPrice, quotePrice,
          notional, signed, multiplier));
      gross += Math.abs(notional);
      net += signed;
    }

    return new OrderNotionalSummary(gross, net, legs, multiLeg);
  }

  private List<BrokerOrderLegRequest> legsFromRequest(BrokerOrderRequest request) {
    List<BrokerOrderLegRequest> legs = request.getLegs();
    if (legs != null && !legs.isEmpty()) {
      return legs;
    }
    BrokerOrderLegRequest leg = new BrokerOrderLegRequest();
    leg.setSymbol(request.getSymbol());
    leg.setSide(request.getSide());
    leg.setQuantity(request.getQuantity());
    leg.setAssetClass(request.getAssetClass());
    leg.setLimitPrice(request.getLimitPrice());
    leg.setStopPrice(request.getStopPrice());
    leg.setMetadata(request.getMetadata());
    return List.of(leg);
  }

  private Map<String, Double> fetchLatestPrices(List<BrokerOrderLegRequest> legs) {
    Map<String, Double> prices = new LinkedHashMap<>();
    List<String> symbols = new ArrayList<>();
    for (BrokerOrderLegRequest leg : legs) {
      if (leg == null || leg.getSymbol() == null || leg.getSymbol().isBlank()) continue;
      String symbol = leg.getSymbol().trim().toUpperCase();
      if (!symbols.contains(symbol)) {
        symbols.add(symbol);
      }
    }
    if (symbols.isEmpty()) {
      return prices;
    }
    try {
      LatestQuotesResult quotes = marketData.latestQuotes(symbols);
      if (quotes != null) {
        for (LatestQuotesResult.QuoteSnapshot snapshot : quotes.quotes()) {
          MarketQuote quote = snapshot.quote();
          if (quote != null && quote.symbol() != null) {
            prices.put(quote.symbol().trim().toUpperCase(), quote.price());
          }
        }
      }
    } catch (Exception ignored) {
    }
    return prices;
  }

  private LegPriceContext resolveLegPrice(BrokerOrderLegRequest leg,
                                          BrokerOrderPreview preview,
                                          Map<String, Double> latest,
                                          boolean allowPreviewPrice) {
    Double explicitPrice = null;
    if (leg.getLimitPrice() != null && leg.getLimitPrice() > 0.0) {
      explicitPrice = leg.getLimitPrice();
    } else if (leg.getStopPrice() != null && leg.getStopPrice() > 0.0) {
      explicitPrice = leg.getStopPrice();
    }
    Double quotePrice = null;
    if (leg.getSymbol() != null && latest != null) {
      quotePrice = latest.get(leg.getSymbol().trim().toUpperCase());
    }
    Double effective = explicitPrice;
    if (effective == null && allowPreviewPrice && preview != null && preview.getPrice() != null
        && preview.getPrice() > 0.0) {
      effective = preview.getPrice();
    }
    if (effective == null) {
      effective = quotePrice;
    }
    return new LegPriceContext(explicitPrice, quotePrice, effective);
  }

  private double resolveContractMultiplier(com.alphamath.portfolio.domain.execution.AssetClass assetClass,
                                           Map<String, Object> legMetadata,
                                           Map<String, Object> orderMetadata) {
    if (assetClass != null && assetClass.name().equals("OPTIONS")) {
      return 100.0;
    }
    Double fromMeta = parseMultiplier(legMetadata);
    if (fromMeta != null) {
      return fromMeta;
    }
    fromMeta = parseMultiplier(orderMetadata);
    return fromMeta == null ? 1.0 : fromMeta;
  }

  private Double parseMultiplier(Map<String, Object> metadata) {
    if (metadata == null || metadata.isEmpty()) {
      return null;
    }
    Object raw = metadata.get("contractMultiplier");
    if (raw == null) {
      return null;
    }
    try {
      return Double.parseDouble(raw.toString());
    } catch (Exception ignored) {
      return null;
    }
  }

  private List<Map<String, Object>> legsToMaps(List<LegEstimate> legs) {
    List<Map<String, Object>> out = new ArrayList<>();
    for (LegEstimate leg : legs) {
      if (leg == null) continue;
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("symbol", leg.symbol);
      entry.put("side", leg.side == null ? null : leg.side.name());
      entry.put("quantity", round(leg.quantity, 6));
      entry.put("price", leg.price == null ? null : round(leg.price, 6));
      entry.put("notional", round(leg.notional, 2));
      entry.put("assetClass", leg.assetClass == null ? null : leg.assetClass.name());
      entry.put("contractMultiplier", round(leg.multiplier, 4));
      out.add(entry);
    }
    return out;
  }

  private double estimateEquity(Double cash, List<BrokerPosition> positions) {
    double total = cash == null ? 0.0 : cash;
    if (positions != null) {
      for (BrokerPosition pos : positions) {
        if (pos == null) continue;
        Double value = pos.getMarketValue();
        if (value != null) {
          total += value;
        } else if (pos.getMarketPrice() != null) {
          total += pos.getMarketPrice() * pos.getQuantity();
        }
      }
    }
    return total;
  }

  private PolicyCheck policyCheck(String name, boolean pass, String detail) {
    return policyCheck(name, pass ? CheckStatus.PASS : CheckStatus.FAIL, detail);
  }

  private PolicyCheck policyCheck(String name, CheckStatus status, String detail) {
    PolicyCheck check = new PolicyCheck();
    check.setName(name);
    check.setStatus(status == null ? CheckStatus.WARN : status);
    check.setDetail(detail);
    return check;
  }

  private String resolvePrimarySymbol(BrokerOrderRequest request) {
    if (request.getSymbol() != null && !request.getSymbol().isBlank()) {
      return request.getSymbol().trim().toUpperCase();
    }
    if (request.getLegs() != null) {
      for (var leg : request.getLegs()) {
        if (leg != null && leg.getSymbol() != null && !leg.getSymbol().isBlank()) {
          return leg.getSymbol().trim().toUpperCase();
        }
      }
    }
    return null;
  }

  private Double resolvePrice(BrokerOrderPreview preview, BrokerOrderRequest request, String symbol) {
    if (preview != null && preview.getPrice() != null && preview.getPrice() > 0.0) {
      return preview.getPrice();
    }
    if (request.getLimitPrice() != null && request.getLimitPrice() > 0.0) {
      return request.getLimitPrice();
    }
    if (request.getStopPrice() != null && request.getStopPrice() > 0.0) {
      return request.getStopPrice();
    }
    if (symbol == null || symbol.isBlank()) {
      return null;
    }
    try {
      LatestQuotesResult quotes = marketData.latestQuotes(List.of(symbol));
      if (quotes != null) {
        for (LatestQuotesResult.QuoteSnapshot snapshot : quotes.quotes()) {
          MarketQuote quote = snapshot.quote();
          if (quote != null && quote.symbol().equalsIgnoreCase(symbol)) {
            return quote.price();
          }
        }
      }
    } catch (Exception ignored) {
    }
    return null;
  }

  private double estimateNotional(BrokerOrderRequest request, Double price) {
    if (price == null || price <= 0.0) {
      return 0.0;
    }
    double qty = Math.max(0.0, request.getQuantity());
    double multiplier = 1.0;
    if (request.getAssetClass() != null && request.getAssetClass().name().equals("OPTIONS")) {
      multiplier = 100.0;
    } else if (request.getMetadata() != null && request.getMetadata().containsKey("contractMultiplier")) {
      try {
        multiplier = Double.parseDouble(request.getMetadata().get("contractMultiplier").toString());
      } catch (Exception ignored) {
      }
    }
    return qty * price * multiplier;
  }

  private double estimateFees(String providerId, com.alphamath.portfolio.domain.execution.AssetClass assetClass,
                              double quantity, double notional) {
    if (notional <= 0.0) {
      return 0.0;
    }
    return providerPolicy.estimateCommission(providerId, assetClass, quantity, notional, feeBps);
  }

  private double orderCashDelta(BrokerOrderRequest request, double notional, double fees) {
    TradeSide side = request.getSide() == null ? TradeSide.BUY : request.getSide();
    if (side == TradeSide.SELL) {
      return Math.max(0.0, notional) - Math.max(0.0, fees);
    }
    return -Math.max(0.0, notional) - Math.max(0.0, fees);
  }

  private Double resolveBalance(Map<String, Double> balances, String currency) {
    if (balances == null || balances.isEmpty()) {
      return null;
    }
    if (currency != null && balances.containsKey(currency)) {
      return balances.get(currency);
    }
    for (String key : List.of("available_cash", "buying_power", "cash", "settled_cash")) {
      if (balances.containsKey(key)) {
        return balances.get(key);
      }
    }
    return balances.values().stream().findFirst().orElse(null);
  }

  private Double resolveBalanceByKeys(Map<String, Double> balances, List<String> keys) {
    if (balances == null || balances.isEmpty() || keys == null || keys.isEmpty()) {
      return null;
    }
    for (String key : keys) {
      if (key != null && balances.containsKey(key)) {
        return balances.get(key);
      }
    }
    return null;
  }

  private double round(double value, int places) {
    double pow = Math.pow(10, places);
    return Math.round(value * pow) / pow;
  }

  private static final class LegEstimate {
    private final String symbol;
    private final TradeSide side;
    private final double quantity;
    private final com.alphamath.portfolio.domain.execution.AssetClass assetClass;
    private final Double price;
    private final Double explicitPrice;
    private final Double quotePrice;
    private final double notional;
    private final double signedNotional;
    private final double multiplier;

    private LegEstimate(String symbol,
                        TradeSide side,
                        double quantity,
                        com.alphamath.portfolio.domain.execution.AssetClass assetClass,
                        Double price,
                        Double explicitPrice,
                        Double quotePrice,
                        double notional,
                        double signedNotional,
                        double multiplier) {
      this.symbol = symbol;
      this.side = side;
      this.quantity = quantity;
      this.assetClass = assetClass;
      this.price = price;
      this.explicitPrice = explicitPrice;
      this.quotePrice = quotePrice;
      this.notional = notional;
      this.signedNotional = signedNotional;
      this.multiplier = multiplier;
    }
  }

  private static final class OrderNotionalSummary {
    private final double grossNotional;
    private final double netNotional;
    private final List<LegEstimate> legs;
    private final boolean multiLeg;

    private OrderNotionalSummary(double grossNotional,
                                 double netNotional,
                                 List<LegEstimate> legs,
                                 boolean multiLeg) {
      this.grossNotional = grossNotional;
      this.netNotional = netNotional;
      this.legs = legs;
      this.multiLeg = multiLeg;
    }
  }

  private static final class LegAggregate {
    private String symbol;
    private double deltaQuantity;
    private Double price;
  }

  private BrokerOrderLegEntity toEntity(BrokerOrderLeg leg) {
    BrokerOrderLegEntity entity = new BrokerOrderLegEntity();
    entity.setId(leg.getId() == null ? UUID.randomUUID().toString() : leg.getId());
    entity.setOrderId(leg.getOrderId());
    entity.setInstrumentId(leg.getInstrumentId());
    entity.setSymbol(leg.getSymbol());
    entity.setAssetClass(leg.getAssetClass());
    entity.setSide(leg.getSide());
    entity.setQuantity(leg.getQuantity());
    entity.setLimitPrice(leg.getLimitPrice());
    entity.setStopPrice(leg.getStopPrice());
    entity.setOptionType(leg.getOptionType());
    entity.setStrike(leg.getStrike());
    entity.setExpiry(leg.getExpiry());
    entity.setMetadataJson(JsonUtils.toJson(leg.getMetadata()));
    return entity;
  }

  private BrokerOrderLeg toDto(BrokerOrderLegEntity entity) {
    BrokerOrderLeg leg = new BrokerOrderLeg();
    leg.setId(entity.getId());
    leg.setOrderId(entity.getOrderId());
    leg.setInstrumentId(entity.getInstrumentId());
    leg.setSymbol(entity.getSymbol());
    leg.setAssetClass(entity.getAssetClass());
    leg.setSide(entity.getSide());
    leg.setQuantity(entity.getQuantity());
    leg.setLimitPrice(entity.getLimitPrice());
    leg.setStopPrice(entity.getStopPrice());
    leg.setOptionType(entity.getOptionType());
    leg.setStrike(entity.getStrike());
    leg.setExpiry(entity.getExpiry());
    leg.setMetadata(parseMetadata(entity.getMetadataJson()));
    return leg;
  }

  private List<String> parseStringList(String json) {
    if (json == null || json.isBlank()) {
      return new ArrayList<>();
    }
    try {
      return JsonUtils.fromJson(json, new TypeReference<List<String>>() {});
    } catch (Exception e) {
      return new ArrayList<>();
    }
  }

  private Map<String, Double> parseBalances(String json) {
    if (json == null || json.isBlank()) {
      return new LinkedHashMap<>();
    }
    try {
      return JsonUtils.fromJson(json, new TypeReference<Map<String, Double>>() {});
    } catch (Exception e) {
      return new LinkedHashMap<>();
    }
  }

  private Map<String, Object> parseMetadata(String json) {
    if (json == null || json.isBlank()) {
      return new LinkedHashMap<>();
    }
    try {
      return JsonUtils.fromJson(json, new TypeReference<Map<String, Object>>() {});
    } catch (Exception e) {
      return new LinkedHashMap<>();
    }
  }

  private List<com.alphamath.portfolio.domain.execution.AssetClass> parseAssetClasses(String json) {
    if (json == null || json.isBlank()) {
      return new ArrayList<>();
    }
    try {
      return JsonUtils.fromJson(json, new TypeReference<List<com.alphamath.portfolio.domain.execution.AssetClass>>() {});
    } catch (Exception e) {
      return new ArrayList<>();
    }
  }

  private static class LegPriceContext {
    private final Double explicitPrice;
    private final Double quotePrice;
    private final Double effectivePrice;

    private LegPriceContext(Double explicitPrice, Double quotePrice, Double effectivePrice) {
      this.explicitPrice = explicitPrice;
      this.quotePrice = quotePrice;
      this.effectivePrice = effectivePrice;
    }
  }
}
