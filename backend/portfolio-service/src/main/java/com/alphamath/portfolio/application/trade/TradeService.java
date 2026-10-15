package com.alphamath.portfolio.application.trade;

import com.alphamath.portfolio.application.audit.AuditService;
import com.alphamath.portfolio.application.compliance.ComplianceService;
import com.alphamath.portfolio.application.execution.ExecutionService;
import com.alphamath.portfolio.application.execution.ExecutionApprovalGuard;
import com.alphamath.portfolio.application.execution.BestExecutionService;
import com.alphamath.portfolio.application.policy.ProviderPolicyService;
import com.alphamath.portfolio.application.surveillance.SurveillanceService;
import com.alphamath.portfolio.domain.execution.AssetClass;
import com.alphamath.portfolio.domain.execution.OrderType;
import com.alphamath.portfolio.domain.execution.Region;
import com.alphamath.portfolio.domain.execution.TimeInForce;
import com.alphamath.portfolio.domain.trade.AiRecommendation;
import com.alphamath.portfolio.domain.trade.CheckStatus;
import com.alphamath.portfolio.domain.trade.DecisionAction;
import com.alphamath.portfolio.domain.trade.ExecutionMode;
import com.alphamath.portfolio.domain.trade.PaperAccount;
import com.alphamath.portfolio.domain.trade.PolicyCheck;
import com.alphamath.portfolio.domain.trade.TradeDecisionRequest;
import com.alphamath.portfolio.domain.trade.TradeExecution;
import com.alphamath.portfolio.domain.trade.TradeOrder;
import com.alphamath.portfolio.domain.trade.TradeProposal;
import com.alphamath.portfolio.domain.trade.TradeProposalRequest;
import com.alphamath.portfolio.domain.trade.TradeSeedRequest;
import com.alphamath.portfolio.domain.trade.TradeSide;
import com.alphamath.portfolio.domain.trade.TradeStatus;
import com.alphamath.portfolio.infrastructure.ai.AiForecastService;
import com.alphamath.portfolio.infrastructure.persistence.JsonUtils;
import com.alphamath.portfolio.infrastructure.persistence.PortfolioAccountEntity;
import com.alphamath.portfolio.infrastructure.persistence.PortfolioAccountRepository;
import com.alphamath.portfolio.infrastructure.persistence.PortfolioPositionEntity;
import com.alphamath.portfolio.infrastructure.persistence.PortfolioPositionRepository;
import com.alphamath.portfolio.infrastructure.persistence.TradeOrderEntity;
import com.alphamath.portfolio.infrastructure.persistence.TradeOrderRepository;
import com.alphamath.portfolio.infrastructure.persistence.TradeProposalEntity;
import com.alphamath.portfolio.infrastructure.persistence.TradeProposalRepository;
import com.alphamath.portfolio.math.BlackLitterman;
import com.alphamath.portfolio.math.MathUtils;
import com.alphamath.portfolio.math.Optimizers;
import com.alphamath.portfolio.security.TenantContext;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class TradeService {
  private static final String ACCOUNT_TYPE_PAPER = "PAPER";
  private static final String ACCOUNT_STATUS_ACTIVE = "ACTIVE";
  private static final String DEFAULT_CURRENCY = "USD";

  private final PortfolioAccountRepository accounts;
  private final PortfolioPositionRepository positions;
  private final TradeProposalRepository proposals;
  private final TradeOrderRepository orders;
  private final ComplianceService compliance;
  private final ExecutionService execution;
  private final AiForecastService aiForecast;
  private final AuditService audit;
  private final ProviderPolicyService providerPolicy;
  private final SurveillanceService surveillance;
  private final BestExecutionService bestExecution;
  private final TenantContext tenantContext;
  private final double feeBps;

  public TradeService(ComplianceService compliance, ExecutionService execution, AiForecastService aiForecast,
                      ProviderPolicyService providerPolicy,
                      SurveillanceService surveillance,
                      BestExecutionService bestExecution,
                      PortfolioAccountRepository accounts, PortfolioPositionRepository positions,
                      TradeProposalRepository proposals, TradeOrderRepository orders,
                      AuditService audit,
                      TenantContext tenantContext,
                      @Value("${alphamath.platform.feeBps:50}") double feeBps) {
    this.compliance = compliance;
    this.execution = execution;
    this.aiForecast = aiForecast;
    this.accounts = accounts;
    this.positions = positions;
    this.proposals = proposals;
    this.orders = orders;
    this.audit = audit;
    this.providerPolicy = providerPolicy;
    this.surveillance = surveillance;
    this.bestExecution = bestExecution;
    this.tenantContext = tenantContext;
    this.feeBps = Math.max(0.0, feeBps);
  }

  public PaperAccount getAccount(String userId) {
    PortfolioAccountEntity entity = getOrCreateAccount(userId);
    List<PortfolioPositionEntity> rows = positions.findByAccountIdOrderBySymbolAsc(entity.getId());
    return toPaperAccount(entity, rows);
  }

  @Transactional
  public PaperAccount seedAccount(String userId, TradeSeedRequest req) {
    PortfolioAccountEntity account = getOrCreateAccount(userId);
    account.setCash(req.getCash());
    account.setUpdatedAt(Instant.now());
    accounts.save(account);

    positions.deleteByAccountId(account.getId());
    List<PortfolioPositionEntity> batch = buildPositions(account.getId(), req.getPositions());
    positions.saveAll(batch);

    audit.record(userId, userId, "ACCOUNT_SEEDED", "portfolio_account", account.getId(),
        Map.of("cash", req.getCash(), "positions", batch.size()));

    return toPaperAccount(account, batch);
  }

  public TradeProposal getProposal(String userId, String id) {
    TradeProposalEntity entity = proposals.findById(id).orElse(null);
    String orgId = tenantContext.getOrgId();
    if (entity == null || !tenantContext.owns(userId, entity.getUserId(), entity.getOrgId())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Proposal not found");
    }
    List<TradeOrderEntity> orderRows = orders.findByProposalIdOrderByCreatedAtAsc(entity.getId());
    return fromEntity(entity, orderRows);
  }

  @Transactional
  public TradeProposal createProposal(String userId, TradeProposalRequest req) {
    validateRequest(req);

    List<String> symbols = req.getSymbols();
    double[] mu = MathUtils.toVector(req.getMu());
    double[][] cov = MathUtils.toMatrix(req.getCov());
    double[] w = computeWeights(req, mu, cov);

    PortfolioAccountEntity account = getOrCreateAccount(userId);
    Map<String, Double> currentPositions = loadPositions(account.getId());
    double cash = account.getCash();

    Map<String, Double> prices = new LinkedHashMap<>();
    Map<String, Double> currentValues = new LinkedHashMap<>();
    double totalEquity = cash;
    for (String symbol : symbols) {
      Double price = req.getPrices().get(symbol);
      if (price == null || price <= 0) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing or invalid price for " + symbol);
      }
      prices.put(symbol, price);

      double qty = currentPositions.getOrDefault(symbol, 0.0);
      double value = qty * price;
      currentValues.put(symbol, value);
      totalEquity += value;
    }

    if (totalEquity <= 0.0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account equity must be positive");
    }

    Map<String, Double> currentWeights = new LinkedHashMap<>();
    Map<String, Double> targetWeights = new LinkedHashMap<>();
    for (int i = 0; i < symbols.size(); i++) {
      String symbol = symbols.get(i);
      currentWeights.put(symbol, currentValues.get(symbol) / totalEquity);
      targetWeights.put(symbol, w[i]);
    }

    double expectedReturn = 0.0;
    for (int i = 0; i < mu.length; i++) expectedReturn += w[i] * mu[i];
    double variance = MathUtils.quadForm(cov, w);

    TradeProposal proposal = new TradeProposal();
    proposal.setId(UUID.randomUUID().toString());
    proposal.setUserId(userId);
    proposal.setCreatedAt(Instant.now());
    proposal.setStatus(TradeStatus.PENDING_USER);
    proposal.setExecutionMode(req.getExecutionMode() == null ? ExecutionMode.PAPER : req.getExecutionMode());
    proposal.setRegion(req.getRegion() == null ? proposal.getRegion() : req.getRegion());
    proposal.setAssetClass(req.getAssetClass() == null ? proposal.getAssetClass() : req.getAssetClass());
    proposal.setProviderPreference(req.getProviderPreference());
    proposal.setOrderType(req.getOrderType() == null ? proposal.getOrderType() : req.getOrderType());
    proposal.setTimeInForce(req.getTimeInForce() == null ? proposal.getTimeInForce() : req.getTimeInForce());
    proposal.setSymbols(new ArrayList<>(symbols));
    proposal.setPrices(prices);
    proposal.setCurrentWeights(currentWeights);
    proposal.setTargetWeights(targetWeights);
    proposal.setExpectedReturn(expectedReturn);
    proposal.setVariance(variance);
    proposal.setTotalEquity(totalEquity);

    RiskOverlay overlay = applyRiskOverlay(req);
    buildOrdersAndChecks(req, proposal, currentValues, cash);
    if (overlay != null && proposal.getPolicyChecks() != null) {
      proposal.getPolicyChecks().add(buildCheck("Risk overlay", overlay.scale >= 0.7,
          "Scaled limits x" + round(overlay.scale, 2)
              + " (vol=" + round(overlay.volatility, 4)
              + ", dd=" + round(overlay.maxDrawdown, 4) + ")"));
    }
    if (proposal.getExecutionMode() == ExecutionMode.LIVE) {
      proposal.getPolicyChecks().addAll(compliance.complianceChecks(userId));
      boolean brokerLinked = execution.hasLinkedAccount(userId, proposal.getRegion(), proposal.getAssetClass(), proposal.getProviderPreference());
      proposal.getPolicyChecks().add(buildCheck("Broker account", brokerLinked, brokerLinked ? "Linked" : "Not linked"));
    }
    proposal.setAi(buildAiRecommendation(req, proposal));
    boolean isJpm = providerPolicy.isJpm(proposal.getProviderPreference());
    if (isJpm && proposal.getAssetClass() == AssetClass.OPTIONS) {
      proposal.setDisclaimer("AI proposal requires user confirmation. Commission $0.65/contract for options. Not financial advice.");
    } else if (isJpm) {
      proposal.setDisclaimer("AI proposal requires user confirmation. Commission-free for stocks/ETFs/funds. Not financial advice.");
    } else {
      proposal.setDisclaimer("AI proposal requires user confirmation. Platform fee " + round(feeBps, 2)
          + " bps applies to buy and sell notional. Not financial advice.");
    }

    TradeProposalEntity entity = toEntity(proposal, account.getId());
    proposals.save(entity);
    orders.saveAll(toOrderEntities(proposal.getId(), proposal.getOrders()));

    audit.record(userId, userId, "PROPOSAL_CREATED", "portfolio_trade_proposal", proposal.getId(),
        Map.of("status", proposal.getStatus().name(), "symbols", proposal.getSymbols().size()));

    return proposal;
  }

  @Transactional
  public TradeProposal decide(String userId, String id, TradeDecisionRequest req) {
    TradeProposalEntity entity = proposals.findByIdForUpdate(id).orElse(null);
    if (entity == null || !tenantContext.owns(userId, entity.getUserId(), entity.getOrgId())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Proposal not found");
    }
    if (req == null || req.getAction() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "decision action is required");
    }
    List<TradeOrderEntity> orderRows = orders.findByProposalIdOrderByCreatedAtAsc(entity.getId());
    TradeProposal p = fromEntity(entity, orderRows);

    if (p.getStatus() != TradeStatus.PENDING_USER) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Proposal is not pending");
    }

    if (req.getAction() == DecisionAction.WAIT) {
      return p;
    }
    if (req.getAction() == DecisionAction.DECLINE) {
      p.setStatus(TradeStatus.DECLINED);
      proposals.save(toEntity(p, entity.getAccountId()));
      audit.record(userId, userId, "PROPOSAL_DECLINED", "portfolio_trade_proposal", p.getId(), Map.of());
      return p;
    }

    p.setStatus(TradeStatus.APPROVED);

    if (p.getExecutionMode() == ExecutionMode.LIVE) {
      compliance.requireLiveEligibility(userId);
      if (!execution.hasLinkedAccount(userId, p.getRegion(), p.getAssetClass(), p.getProviderPreference())) {
        throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "Broker account not linked");
      }
      p.setApprovedBy(userId);
      p.setApprovedAt(Instant.now());
      p.setApprovalHash(ExecutionApprovalGuard.hash(entity, orderRows));
      proposals.saveAndFlush(toEntity(p, entity.getAccountId()));
      var intent = execution.createIntent(userId, p);
      p.setExecutionIntentId(intent.getId());
      p.setStatus(TradeStatus.SUBMITTED);
      proposals.save(toEntity(p, entity.getAccountId()));
      audit.record(userId, userId, "PROPOSAL_APPROVED", "portfolio_trade_proposal", p.getId(),
          Map.of("executionIntentId", intent.getId()));
      return p;
    }

    audit.record(userId, userId, "PROPOSAL_APPROVED", "portfolio_trade_proposal", p.getId(),
        Map.of("executionMode", p.getExecutionMode().name()));
    TradeExecution exec = executeProposal(userId, p, entity.getAccountId());
    p.setExecution(exec);
    p.setStatus(TradeStatus.EXECUTED);
    proposals.save(toEntity(p, entity.getAccountId()));

    audit.record(userId, userId, "PROPOSAL_EXECUTED", "portfolio_trade_proposal", p.getId(),
        Map.of("fills", exec.getFilledOrders().size(), "feeTotal", exec.getFeeTotal()));
    return p;
  }

  private void validateRequest(TradeProposalRequest req) {
    providerPolicy.enforceAssetClass(req.getProviderPreference(),
        req.getAssetClass() == null ? AssetClass.EQUITY : req.getAssetClass());
    int n = req.getSymbols().size();
    if (req.getMu().size() != n) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "mu length must match symbols");
    }
    if (req.getCov().size() != n) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cov must be NxN");
    }
    for (var row : req.getCov()) {
      if (row.size() != n) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cov must be NxN");
      }
    }
  }

  private double[] computeWeights(TradeProposalRequest req, double[] mu, double[][] cov) {
    return switch (req.getMethod()) {
      case MIN_VARIANCE -> Optimizers.optimizeMinVariance(cov, req.getMinWeight(), req.getMaxWeight(), 5000, 0.02);
      case RISK_PARITY -> Optimizers.optimizeRiskParity(cov, req.getMinWeight(), req.getMaxWeight(), 5000);
      case KELLY_APPROX -> Optimizers.optimizeKellyApprox(mu, cov,
          req.getFractionalKelly() == null ? 0.25 : req.getFractionalKelly(),
          req.getMinWeight(), req.getMaxWeight());
      case BLACK_LITTERMAN_MEANVAR -> {
        if (req.getP() == null || req.getP().isEmpty()) {
          yield Optimizers.optimizeMeanVariancePGD(mu, cov, req.getRiskAversion(), req.getMinWeight(), req.getMaxWeight(), 4000, 0.02);
        }
        double[][] Pm = new double[req.getP().size()][mu.length];
        for (int i = 0; i < req.getP().size(); i++) {
          var row = req.getP().get(i);
          for (int j = 0; j < mu.length; j++) Pm[i][j] = row.get(j);
        }
        double[] qv = MathUtils.toVector(req.getQ());
        double[] od = MathUtils.toVector(req.getOmegaDiag());
        double tau = req.getTau() == null ? 0.05 : req.getTau();
        var bl = BlackLitterman.posterior(mu, cov, tau, Pm, qv, od);
        yield Optimizers.optimizeMeanVariancePGD(bl.posteriorMu, bl.posteriorCov, req.getRiskAversion(),
            req.getMinWeight(), req.getMaxWeight(), 5000, 0.02);
      }
      default -> Optimizers.optimizeMeanVariancePGD(mu, cov, req.getRiskAversion(),
          req.getMinWeight(), req.getMaxWeight(), 4000, 0.02);
    };
  }

  private void buildOrdersAndChecks(TradeProposalRequest req, TradeProposal proposal,
                                    Map<String, Double> currentValues, double cash) {
    boolean isJpm = providerPolicy.isJpm(proposal.getProviderPreference());
    double feeBpsFallback = feeBps;
    double totalEquity = proposal.getTotalEquity();
    double minTrade = req.getMinTradeValue() == null ? 0.0 : req.getMinTradeValue();
    double maxTurnover = req.getMaxTurnover() == null ? 1.0 : req.getMaxTurnover();
    double maxTradePct = req.getMaxTradePctOfEquity() == null ? 1.0 : req.getMaxTradePctOfEquity();
    double maxTradeNotional = Math.max(0.0, maxTradePct * totalEquity);

    boolean minTradeSkipped = false;
    boolean oversizedTrades = false;

    double buyNotional = 0.0;
    double sellNotional = 0.0;
    double turnover = 0.0;

    List<TradeOrder> orders = new ArrayList<>();
    for (String symbol : proposal.getSymbols()) {
      double price = proposal.getPrices().get(symbol);
      double current = currentValues.get(symbol);
      double targetValue = proposal.getTargetWeights().get(symbol) * totalEquity;
      double delta = targetValue - current;
      turnover += Math.abs(delta) / totalEquity;

      if (Math.abs(delta) < minTrade) {
        minTradeSkipped = true;
        continue;
      }

      TradeOrder order = new TradeOrder();
      order.setSymbol(symbol);
      order.setPrice(price);
      order.setSide(delta >= 0 ? TradeSide.BUY : TradeSide.SELL);
      double notional = Math.abs(delta);
      double qty = notional / price;

      if (maxTradeNotional > 0 && notional > maxTradeNotional) {
        double factor = maxTradeNotional / notional;
        notional *= factor;
        qty *= factor;
        oversizedTrades = true;
      }

      order.setQuantity(qty);
      order.setNotional(notional);
      order.setFee(providerPolicy.estimateCommission(
          proposal.getProviderPreference(),
          proposal.getAssetClass(),
          qty,
          notional,
          feeBpsFallback
      ));
      orders.add(order);

      if (order.getSide() == TradeSide.BUY) buyNotional += notional;
      else sellNotional += notional;
    }

    double sellFeeTotal = 0.0;
    for (TradeOrder order : orders) {
      if (order.getSide() == TradeSide.SELL) sellFeeTotal += order.getFee();
    }
    double availableCash = cash + Math.max(0.0, sellNotional - sellFeeTotal);
    double scaledBuyFactor = 1.0;
    double buyCost = 0.0;
    for (TradeOrder order : orders) {
      if (order.getSide() == TradeSide.BUY) {
        buyCost += order.getNotional() + order.getFee();
      }
    }
    if (buyCost > availableCash && buyCost > 1e-12) {
      scaledBuyFactor = availableCash / buyCost;
      buyNotional = 0.0;
      for (TradeOrder order : orders) {
        if (order.getSide() == TradeSide.BUY) {
          order.setQuantity(order.getQuantity() * scaledBuyFactor);
          order.setNotional(order.getNotional() * scaledBuyFactor);
          order.setFee(order.getFee() * scaledBuyFactor);
          buyNotional += order.getNotional();
        }
      }
    }

    double feeTotal = 0.0;
    for (TradeOrder order : orders) {
      feeTotal += order.getFee();
    }

    proposal.setOrders(orders);
    proposal.setTurnover(turnover);
    proposal.setScaledBuyFactor(scaledBuyFactor);
    proposal.setFeeBps(isJpm ? 0.0 : feeBps);
    proposal.setFeeTotal(feeTotal);

    List<PolicyCheck> checks = new ArrayList<>();
    checks.add(buildCheck("Turnover", turnover <= maxTurnover, "Turnover=" + round(turnover, 4) + " max=" + maxTurnover));
    if (isJpm && proposal.getAssetClass() == AssetClass.OPTIONS) {
      checks.add(buildCheck("Commission", true, "$0.65/contract options commission = " + round(feeTotal, 2)));
    } else if (isJpm) {
      checks.add(buildCheck("Commission", true, "$0 commission for stocks/ETFs/funds"));
    } else {
      checks.add(buildCheck("Platform fee", true, "Fee " + round(feeBps, 2) + " bps (buy+sell) = " + round(feeTotal, 2)));
    }
    if (scaledBuyFactor < 1.0) {
      checks.add(buildCheck("Cash coverage", false, "Buys scaled by " + round(scaledBuyFactor, 3) + " to fit cash"));
    } else {
      checks.add(buildCheck("Cash coverage", true, "Buys fit available cash"));
    }
    if (oversizedTrades) {
      checks.add(buildCheck("Max trade size", false, "Trades clipped to " + round(maxTradeNotional, 2)));
    } else {
      checks.add(buildCheck("Max trade size", true, "All trades within limit"));
    }
    if (minTradeSkipped) {
      checks.add(buildCheck("Min trade value", false, "Some trades skipped under " + round(minTrade, 2)));
    } else {
      checks.add(buildCheck("Min trade value", true, "All trades above minimum"));
    }
    if (orders.isEmpty()) {
      checks.add(buildCheck("No-op", false, "No actionable trades generated"));
    }
    proposal.setPolicyChecks(checks);
  }

  private TradeExecution executeProposal(String userId, TradeProposal proposal, String accountId) {
    PortfolioAccountEntity account = accountId == null ? getOrCreateAccount(userId)
        : accounts.findById(accountId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
    if (!tenantContext.owns(userId, account.getUserId(), account.getOrgId())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
    }

    boolean isJpm = providerPolicy.isJpm(proposal.getProviderPreference());

    Map<String, Double> currentPositions = loadPositions(account.getId());
    double cash = account.getCash();
    Map<String, Double> positionsAfter = new LinkedHashMap<>(currentPositions);

    List<TradeOrder> filled = new ArrayList<>();
    double totalFees = 0.0;

    for (TradeOrder order : proposal.getOrders()) {
      if (order.getSide() != TradeSide.SELL) continue;
      double held = positionsAfter.getOrDefault(order.getSymbol(), 0.0);
      double qty = Math.min(order.getQuantity(), held);
      double notional = qty * order.getPrice();
      positionsAfter.put(order.getSymbol(), held - qty);
      double fee = providerPolicy.estimateCommission(
          proposal.getProviderPreference(),
          proposal.getAssetClass(),
          qty,
          notional,
          feeBps
      );
      cash += (notional - fee);
      totalFees += fee;

      TradeOrder fill = new TradeOrder();
      fill.setSymbol(order.getSymbol());
      fill.setSide(TradeSide.SELL);
      fill.setPrice(order.getPrice());
      fill.setQuantity(qty);
      fill.setNotional(notional);
      fill.setFee(fee);
      filled.add(fill);
    }

    for (TradeOrder order : proposal.getOrders()) {
      if (order.getSide() != TradeSide.BUY) continue;
      double notional = order.getNotional();
      double fee = order.getFee();
      double cost = notional + fee;
      if (cash <= 1e-9) break;
      double qty = order.getQuantity();
      if (cost > cash) {
        double factor = cash / cost;
        qty *= factor;
        notional *= factor;
        fee *= factor;
        cost = notional + fee;
      }
      double held = positionsAfter.getOrDefault(order.getSymbol(), 0.0);
      positionsAfter.put(order.getSymbol(), held + qty);
      cash -= cost;
      totalFees += fee;

      TradeOrder fill = new TradeOrder();
      fill.setSymbol(order.getSymbol());
      fill.setSide(TradeSide.BUY);
      fill.setPrice(order.getPrice());
      fill.setQuantity(qty);
      fill.setNotional(notional);
      fill.setFee(fee);
      filled.add(fill);
    }

    account.setCash(cash);
    account.setUpdatedAt(Instant.now());
    accounts.save(account);

    positions.deleteByAccountId(account.getId());
    positions.saveAll(buildPositions(account.getId(), positionsAfter));

    TradeExecution exec = new TradeExecution();
    exec.setExecutedAt(Instant.now());
    exec.setFilledOrders(filled);
    exec.setCashAfter(cash);
    exec.setPositionsAfter(new LinkedHashMap<>(positionsAfter));
    exec.setFeeTotal(totalFees);
    exec.setNote("Paper-trade execution filled at provided prices.");

    surveillance.evaluate(userId, proposal.getId(), filled);
    bestExecution.record(userId, proposal.getId(), filled);
    return exec;
  }

  private PolicyCheck buildCheck(String name, boolean pass, String detail) {
    PolicyCheck check = new PolicyCheck();
    check.setName(name);
    check.setStatus(pass ? CheckStatus.PASS : CheckStatus.WARN);
    check.setDetail(detail);
    return check;
  }

  private AiRecommendation buildAiRecommendation(TradeProposalRequest req, TradeProposal proposal) {
    AiRecommendation ai = new AiRecommendation();
    double sharpeLike = proposal.getVariance() <= 1e-12 ? 0.0
        : proposal.getExpectedReturn() / Math.sqrt(proposal.getVariance());
    double confidence = 0.5 + 0.2 * Math.tanh(sharpeLike);
    confidence = Math.max(0.1, Math.min(0.9, confidence));

    boolean aiUsed = false;
    if (req.getReturns() != null && !req.getReturns().isEmpty()) {
      int horizon = req.getAiHorizon() == null ? 1 : req.getAiHorizon();
      var forecast = aiForecast.predict(req.getReturns(), horizon);
      if (forecast != null) {
        aiUsed = true;
        ai.setExpectedReturn(forecast.expectedReturn());
        ai.setVolatility(forecast.volatility());
        ai.setPUp(forecast.pUp());
        ai.setHorizon(horizon);
        ai.setConfidence(forecast.confidence());
        ai.setModel("ai-service");
        ai.setDisclaimer(forecast.disclaimer());
        ai.getReasons().add("AI expected return=" + round(forecast.expectedReturn(), 4));
        ai.getReasons().add("AI volatility=" + round(forecast.volatility(), 4));
        ai.getReasons().add("AI P(up)=" + round(forecast.pUp(), 4));
      }
    }

    if (!aiUsed) {
      ai.setConfidence(confidence);
    }
    ai.setSummary((aiUsed ? "AI-assisted " : "AI proposes a ") + "rebalance using "
        + (req.getMethod() == null ? Optimizers.Method.MEAN_VARIANCE_PGD : req.getMethod()).name()
        + " (" + proposal.getExecutionMode() + "). User confirmation required.");
    ai.getReasons().add("Expected return=" + round(proposal.getExpectedReturn(), 4));
    ai.getReasons().add("Variance=" + round(proposal.getVariance(), 4));
    ai.getReasons().add("Turnover=" + round(proposal.getTurnover(), 4));
    ai.getReasons().add("Platform fee=" + round(proposal.getFeeTotal(), 2));
    return ai;
  }

  private RiskOverlay applyRiskOverlay(TradeProposalRequest req) {
    if (req.getReturns() == null || req.getReturns().size() < 30) {
      return null;
    }
    int horizon = req.getAiHorizon() == null ? 1 : req.getAiHorizon();
    var risk = aiForecast.risk(req.getReturns(), horizon);
    if (risk == null) {
      return null;
    }

    double scale = 1.0 / (1.0 + risk.volatility() * 4.0);
    if (risk.maxDrawdown() > 0.2) {
      scale *= 0.8;
    }
    scale = Math.max(0.3, Math.min(1.0, scale));

    if (req.getMaxTradePctOfEquity() != null) {
      req.setMaxTradePctOfEquity(req.getMaxTradePctOfEquity() * scale);
    }
    if (req.getMaxTurnover() != null) {
      req.setMaxTurnover(req.getMaxTurnover() * scale);
    }

    return new RiskOverlay(scale, risk.volatility(), risk.maxDrawdown());
  }

  private double round(double v, int places) {
    double pow = Math.pow(10, places);
    return Math.round(v * pow) / pow;
  }


  private static class RiskOverlay {
    private final double scale;
    private final double volatility;
    private final double maxDrawdown;

    private RiskOverlay(double scale, double volatility, double maxDrawdown) {
      this.scale = scale;
      this.volatility = volatility;
      this.maxDrawdown = maxDrawdown;
    }
  }

  @Transactional
  public void creditCash(String userId, double amount) {
    PortfolioAccountEntity account = getOrCreateAccount(userId);
    account.setCash(account.getCash() + amount);
    account.setUpdatedAt(Instant.now());
    accounts.save(account);
    audit.record(userId, userId, "CASH_CREDITED", "portfolio_account", account.getId(), Map.of("amount", amount));
  }

  @Transactional
  public void debitCash(String userId, double amount) {
    PortfolioAccountEntity account = getOrCreateAccount(userId);
    if (account.getCash() < amount) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "insufficient cash balance");
    }
    account.setCash(account.getCash() - amount);
    account.setUpdatedAt(Instant.now());
    accounts.save(account);
    audit.record(userId, userId, "CASH_DEBITED", "portfolio_account", account.getId(), Map.of("amount", amount));
  }

  private PortfolioAccountEntity getOrCreateAccount(String userId) {
    String orgId = tenantContext.getOrgId();
    PortfolioAccountEntity found = (orgId == null
        ? accounts.findByUserIdAndAccountType(userId, ACCOUNT_TYPE_PAPER)
        : accounts.findByUserIdAndOrgIdAndAccountType(userId, orgId, ACCOUNT_TYPE_PAPER)).orElseGet(() -> {
      PortfolioAccountEntity created = new PortfolioAccountEntity();
      created.setId(UUID.randomUUID().toString());
      created.setUserId(userId);
      created.setOrgId(tenantContext.getOrgId());
      created.setAccountType(ACCOUNT_TYPE_PAPER);
      created.setStatus(ACCOUNT_STATUS_ACTIVE);
      created.setCash(0.0);
      created.setCurrency(DEFAULT_CURRENCY);
      created.setCreatedAt(Instant.now());
      created.setUpdatedAt(created.getCreatedAt());
      accounts.save(created);
      audit.record(userId, userId, "ACCOUNT_CREATED", "portfolio_account", created.getId(),
          Map.of("accountType", ACCOUNT_TYPE_PAPER, "currency", DEFAULT_CURRENCY));
      return created;
    });
    if (!tenantContext.owns(userId, found.getUserId(), found.getOrgId())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
    }
    return found;
  }

  private Map<String, Double> loadPositions(String accountId) {
    Map<String, Double> out = new LinkedHashMap<>();
    for (PortfolioPositionEntity entity : positions.findByAccountIdOrderBySymbolAsc(accountId)) {
      out.put(entity.getSymbol(), entity.getQuantity());
    }
    return out;
  }

  private List<PortfolioPositionEntity> buildPositions(String accountId, Map<String, Double> positionsMap) {
    List<PortfolioPositionEntity> batch = new ArrayList<>();
    if (positionsMap == null) return batch;
    for (var entry : positionsMap.entrySet()) {
      String symbol = normalizeSymbol(entry.getKey());
      if (symbol == null) continue;
      double qty = entry.getValue() == null ? 0.0 : entry.getValue();
      if (Math.abs(qty) <= 1e-9) continue;
      PortfolioPositionEntity entity = new PortfolioPositionEntity();
      entity.setId(UUID.randomUUID().toString());
      entity.setAccountId(accountId);
      entity.setSymbol(symbol);
      entity.setQuantity(qty);
      entity.setUpdatedAt(Instant.now());
      batch.add(entity);
    }
    return batch;
  }

  private PaperAccount toPaperAccount(PortfolioAccountEntity account, List<PortfolioPositionEntity> rows) {
    PaperAccount acct = new PaperAccount();
    acct.setCash(account.getCash());
    Map<String, Double> positionsMap = new LinkedHashMap<>();
    for (PortfolioPositionEntity row : rows) {
      positionsMap.put(row.getSymbol(), row.getQuantity());
    }
    acct.setPositions(positionsMap);
    acct.setUpdatedAt(account.getUpdatedAt() == null ? Instant.now() : account.getUpdatedAt());
    return acct;
  }

  private TradeProposalEntity toEntity(TradeProposal proposal, String accountId) {
    TradeProposalEntity entity = new TradeProposalEntity();
    entity.setId(proposal.getId());
    entity.setUserId(proposal.getUserId());
    entity.setOrgId(tenantContext.getOrgId());
    entity.setAccountId(accountId);
    entity.setStatus(proposal.getStatus().name());
    entity.setApprovedBy(proposal.getApprovedBy());
    entity.setApprovedAt(proposal.getApprovedAt());
    entity.setApprovalHash(proposal.getApprovalHash());
    entity.setCreatedAt(proposal.getCreatedAt());
    entity.setUpdatedAt(Instant.now());

    entity.setExecutionMode(proposal.getExecutionMode());
    entity.setRegion(proposal.getRegion());
    entity.setAssetClass(proposal.getAssetClass());
    entity.setProviderPreference(proposal.getProviderPreference());
    entity.setOrderType(proposal.getOrderType());
    entity.setTimeInForce(proposal.getTimeInForce());

    entity.setExpectedReturn(proposal.getExpectedReturn());
    entity.setVariance(proposal.getVariance());
    entity.setTotalEquity(proposal.getTotalEquity());
    entity.setTurnover(proposal.getTurnover());
    entity.setScaledBuyFactor(proposal.getScaledBuyFactor());
    entity.setFeeBps(proposal.getFeeBps());
    entity.setFeeTotal(proposal.getFeeTotal());

    AiRecommendation ai = proposal.getAi();
    if (ai != null) {
      entity.setAiSummary(ai.getSummary());
      entity.setAiConfidence(ai.getConfidence());
      entity.setAiExpectedReturn(ai.getExpectedReturn());
      entity.setAiVolatility(ai.getVolatility());
      entity.setAiPUp(ai.getPUp());
      entity.setAiHorizon(ai.getHorizon());
      entity.setAiModel(ai.getModel());
      entity.setAiDisclaimer(ai.getDisclaimer());
    }

    entity.setDisclaimer(proposal.getDisclaimer());
    entity.setExecutionIntentId(proposal.getExecutionIntentId());
    entity.setExecutionJson(proposal.getExecution() == null ? null : JsonUtils.toJson(proposal.getExecution()));

    entity.setSymbolsJson(JsonUtils.toJson(proposal.getSymbols()));
    entity.setPricesJson(JsonUtils.toJson(proposal.getPrices()));
    entity.setCurrentWeightsJson(JsonUtils.toJson(proposal.getCurrentWeights()));
    entity.setTargetWeightsJson(JsonUtils.toJson(proposal.getTargetWeights()));
    entity.setPolicyChecksJson(JsonUtils.toJson(proposal.getPolicyChecks()));

    entity.setPayload(JsonUtils.toJson(proposal));
    return entity;
  }

  private TradeProposal fromEntity(TradeProposalEntity entity, List<TradeOrderEntity> orderRows) {
    if ((entity.getSymbolsJson() == null || entity.getSymbolsJson().isBlank())
        && entity.getPayload() != null && !entity.getPayload().isBlank()) {
      try {
        TradeProposal legacy = JsonUtils.fromJson(entity.getPayload(), TradeProposal.class);
        legacy.setId(entity.getId());
        legacy.setUserId(entity.getUserId());
        legacy.setStatus(TradeStatus.valueOf(entity.getStatus()));
        legacy.setCreatedAt(entity.getCreatedAt());
        return legacy;
      } catch (Exception ignored) {
      }
    }
    TradeProposal proposal = new TradeProposal();
    proposal.setId(entity.getId());
    proposal.setUserId(entity.getUserId());
    proposal.setCreatedAt(entity.getCreatedAt());
    proposal.setStatus(TradeStatus.valueOf(entity.getStatus()));
    proposal.setApprovedBy(entity.getApprovedBy());
    proposal.setApprovedAt(entity.getApprovedAt());
    proposal.setApprovalHash(entity.getApprovalHash());

    proposal.setExecutionMode(entity.getExecutionMode() == null ? ExecutionMode.PAPER : entity.getExecutionMode());
    proposal.setRegion(entity.getRegion() == null ? Region.US : entity.getRegion());
    proposal.setAssetClass(entity.getAssetClass() == null ? AssetClass.EQUITY : entity.getAssetClass());
    proposal.setProviderPreference(entity.getProviderPreference());
    proposal.setOrderType(entity.getOrderType() == null ? OrderType.MARKET : entity.getOrderType());
    proposal.setTimeInForce(entity.getTimeInForce() == null ? TimeInForce.DAY : entity.getTimeInForce());

    proposal.setSymbols(parseList(entity.getSymbolsJson()));
    proposal.setPrices(parseMap(entity.getPricesJson()));
    proposal.setCurrentWeights(parseMap(entity.getCurrentWeightsJson()));
    proposal.setTargetWeights(parseMap(entity.getTargetWeightsJson()));

    proposal.setExpectedReturn(nvl(entity.getExpectedReturn()));
    proposal.setVariance(nvl(entity.getVariance()));
    proposal.setTotalEquity(nvl(entity.getTotalEquity()));
    proposal.setTurnover(nvl(entity.getTurnover()));
    proposal.setScaledBuyFactor(nvl(entity.getScaledBuyFactor()));
    proposal.setFeeBps(nvl(entity.getFeeBps()));
    proposal.setFeeTotal(nvl(entity.getFeeTotal()));

    proposal.setOrders(fromOrderEntities(orderRows));
    proposal.setPolicyChecks(parsePolicyChecks(entity.getPolicyChecksJson()));
    proposal.setAi(toAiRecommendation(entity));
    proposal.setDisclaimer(entity.getDisclaimer());
    proposal.setExecutionIntentId(entity.getExecutionIntentId());
    proposal.setExecution(parseExecution(entity.getExecutionJson()));

    return proposal;
  }

  private List<TradeOrderEntity> toOrderEntities(String proposalId, List<TradeOrder> orders) {
    List<TradeOrderEntity> out = new ArrayList<>();
    if (orders == null) return out;
    for (TradeOrder order : orders) {
      TradeOrderEntity entity = new TradeOrderEntity();
      entity.setId(UUID.randomUUID().toString());
      entity.setProposalId(proposalId);
      entity.setSymbol(normalizeSymbol(order.getSymbol()));
      entity.setSide(order.getSide().name());
      entity.setQuantity(order.getQuantity());
      entity.setPrice(order.getPrice());
      entity.setNotional(order.getNotional());
      entity.setFee(order.getFee());
      entity.setCreatedAt(Instant.now());
      out.add(entity);
    }
    return out;
  }

  private List<TradeOrder> fromOrderEntities(List<TradeOrderEntity> rows) {
    List<TradeOrder> out = new ArrayList<>();
    if (rows == null) return out;
    for (TradeOrderEntity row : rows) {
      TradeOrder order = new TradeOrder();
      order.setSymbol(row.getSymbol());
      order.setSide(TradeSide.valueOf(row.getSide()));
      order.setQuantity(row.getQuantity());
      order.setPrice(row.getPrice());
      order.setNotional(row.getNotional());
      order.setFee(row.getFee());
      out.add(order);
    }
    return out;
  }

  private List<String> parseList(String json) {
    if (json == null || json.isBlank()) return new ArrayList<>();
    return JsonUtils.fromJson(json, new TypeReference<List<String>>() {});
  }

  private Map<String, Double> parseMap(String json) {
    if (json == null || json.isBlank()) return new LinkedHashMap<>();
    return JsonUtils.fromJson(json, new TypeReference<Map<String, Double>>() {});
  }

  private List<PolicyCheck> parsePolicyChecks(String json) {
    if (json == null || json.isBlank()) return new ArrayList<>();
    return JsonUtils.fromJson(json, new TypeReference<List<PolicyCheck>>() {});
  }

  private TradeExecution parseExecution(String json) {
    if (json == null || json.isBlank()) return null;
    return JsonUtils.fromJson(json, TradeExecution.class);
  }

  private AiRecommendation toAiRecommendation(TradeProposalEntity entity) {
    if (entity.getAiSummary() == null
        && entity.getAiConfidence() == null
        && entity.getAiExpectedReturn() == null
        && entity.getAiVolatility() == null
        && entity.getAiPUp() == null
        && entity.getAiHorizon() == null
        && entity.getAiModel() == null
        && entity.getAiDisclaimer() == null) {
      return null;
    }
    AiRecommendation ai = new AiRecommendation();
    ai.setSummary(entity.getAiSummary());
    ai.setConfidence(entity.getAiConfidence() == null ? 0.0 : entity.getAiConfidence());
    ai.setExpectedReturn(entity.getAiExpectedReturn());
    ai.setVolatility(entity.getAiVolatility());
    ai.setPUp(entity.getAiPUp());
    ai.setHorizon(entity.getAiHorizon());
    ai.setModel(entity.getAiModel());
    ai.setDisclaimer(entity.getAiDisclaimer());
    return ai;
  }

  private double nvl(Double value) {
    return value == null ? 0.0 : value;
  }

  private String normalizeSymbol(String symbol) {
    if (symbol == null) return null;
    String trimmed = symbol.trim();
    return trimmed.isEmpty() ? null : trimmed.toUpperCase(Locale.US);
  }
}
