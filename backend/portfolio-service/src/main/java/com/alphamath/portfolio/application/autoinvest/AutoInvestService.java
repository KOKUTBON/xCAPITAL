package com.alphamath.portfolio.application.autoinvest;

import com.alphamath.portfolio.application.audit.AuditService;
import com.alphamath.portfolio.application.marketdata.LatestQuotesResult;
import com.alphamath.portfolio.application.marketdata.MarketDataService;
import com.alphamath.portfolio.application.notification.NotificationService;
import com.alphamath.portfolio.application.policy.ProviderPolicyService;
import com.alphamath.portfolio.application.trade.TradeService;
import com.alphamath.portfolio.domain.autoinvest.AutoInvestFee;
import com.alphamath.portfolio.domain.autoinvest.AutoInvestGoalType;
import com.alphamath.portfolio.domain.autoinvest.AutoInvestPlan;
import com.alphamath.portfolio.domain.autoinvest.AutoInvestPlanRequest;
import com.alphamath.portfolio.domain.autoinvest.AutoInvestPlanStatus;
import com.alphamath.portfolio.domain.autoinvest.AutoInvestRun;
import com.alphamath.portfolio.domain.autoinvest.AutoInvestRunStatus;
import com.alphamath.portfolio.domain.autoinvest.AutoInvestSchedule;
import com.alphamath.portfolio.domain.autoinvest.AutoInvestTrigger;
import com.alphamath.portfolio.domain.marketdata.MarketQuote;
import com.alphamath.portfolio.domain.notification.NotificationType;
import com.alphamath.portfolio.domain.trade.PaperAccount;
import com.alphamath.portfolio.domain.trade.TradeProposal;
import com.alphamath.portfolio.domain.trade.TradeProposalRequest;
import com.alphamath.portfolio.infrastructure.persistence.AutoInvestFeeEntity;
import com.alphamath.portfolio.infrastructure.persistence.AutoInvestFeeRepository;
import com.alphamath.portfolio.infrastructure.persistence.AutoInvestPlanEntity;
import com.alphamath.portfolio.infrastructure.persistence.AutoInvestPlanRepository;
import com.alphamath.portfolio.infrastructure.persistence.AutoInvestRunEntity;
import com.alphamath.portfolio.infrastructure.persistence.AutoInvestRunRepository;
import com.alphamath.portfolio.infrastructure.persistence.JsonUtils;
import com.alphamath.portfolio.security.TenantContext;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.WeekFields;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class AutoInvestService {
  private final AutoInvestPlanRepository plans;
  private final AutoInvestRunRepository runs;
  private final AutoInvestFeeRepository fees;
  private final MarketDataService marketData;
  private final TradeService trade;
  private final NotificationService notifications;
  private final AuditService audit;
  private final ProviderPolicyService providerPolicy;
  private final TenantContext tenantContext;
  private final int minReturns;
  private final int defaultLookback;
  private final int driftIdempotencyHours;
  private final double minimumBalanceDefault;
  private final double advisoryFeeBpsAnnualDefault;
  private final int advisoryFeeChargeDays;

  public AutoInvestService(AutoInvestPlanRepository plans,
                           AutoInvestRunRepository runs,
                           AutoInvestFeeRepository fees,
                           MarketDataService marketData,
                           TradeService trade,
                           NotificationService notifications,
                           AuditService audit,
                           ProviderPolicyService providerPolicy,
                           TenantContext tenantContext,
                           @Value("${alphamath.autoinvest.minReturns:30}") int minReturns,
                           @Value("${alphamath.autoinvest.defaultLookback:90}") int defaultLookback,
                           @Value("${alphamath.autoinvest.driftIdempotencyHours:6}") int driftIdempotencyHours,
                           @Value("${alphamath.autoinvest.minimumBalance:500}") double minimumBalanceDefault,
                           @Value("${alphamath.autoinvest.advisoryFeeBpsAnnual:35}") double advisoryFeeBpsAnnualDefault,
                           @Value("${alphamath.autoinvest.advisoryFeeChargeDays:90}") int advisoryFeeChargeDays) {
    this.plans = plans;
    this.runs = runs;
    this.fees = fees;
    this.marketData = marketData;
    this.trade = trade;
    this.notifications = notifications;
    this.audit = audit;
    this.providerPolicy = providerPolicy;
    this.tenantContext = tenantContext;
    this.minReturns = Math.max(5, minReturns);
    this.defaultLookback = Math.max(30, defaultLookback);
    this.driftIdempotencyHours = Math.max(1, driftIdempotencyHours);
    this.minimumBalanceDefault = Math.max(0.0, minimumBalanceDefault);
    this.advisoryFeeBpsAnnualDefault = Math.max(0.0, advisoryFeeBpsAnnualDefault);
    this.advisoryFeeChargeDays = Math.max(30, advisoryFeeChargeDays);
  }

  public AutoInvestPlan createPlan(String userId, AutoInvestPlanRequest req) {
    AutoInvestPlan plan = new AutoInvestPlan();
    plan.setId(UUID.randomUUID().toString());
    plan.setUserId(userId);
    plan.setOrgId(tenantContext.getOrgId());
    plan.setName(req.getName().trim());
    plan.setStatus(AutoInvestPlanStatus.ACTIVE);
    plan.setGoalType(req.getGoalType() == null ? AutoInvestGoalType.GENERAL_INVESTING : req.getGoalType());
    plan.setSchedule(req.getSchedule());
    plan.setScheduleTimeUtc(req.getScheduleTimeUtc());
    plan.setScheduleDayOfWeek(req.getScheduleDayOfWeek());
    plan.setDriftThreshold(req.getDriftThreshold());
    plan.setReturnsLookback(req.getReturnsLookback());
    plan.setUseMarketData(req.isUseMarketData());
    plan.setUseAiForecast(req.isUseAiForecast());
    plan.setAiHorizon(req.getAiHorizon());
    plan.setMethod(req.getMethod());
    plan.setRiskAversion(req.getRiskAversion());
    plan.setMaxWeight(req.getMaxWeight());
    plan.setMinWeight(req.getMinWeight());
    plan.setMinTradeValue(req.getMinTradeValue());
    plan.setMaxTradePctOfEquity(req.getMaxTradePctOfEquity());
    plan.setMaxTurnover(req.getMaxTurnover());
    double feeBps = req.getAdvisoryFeeBpsAnnual() == null ? advisoryFeeBpsAnnualDefault : req.getAdvisoryFeeBpsAnnual();
    double minBalance = req.getMinimumBalance() == null ? minimumBalanceDefault : req.getMinimumBalance();
    plan.setAdvisoryFeeBpsAnnual(Math.max(0.0, feeBps));
    plan.setMinimumBalance(Math.max(0.0, minBalance));
    plan.setExecutionMode(req.getExecutionMode());
    plan.setRegion(req.getRegion());
    plan.setAssetClass(req.getAssetClass());
    plan.setProviderPreference(req.getProviderPreference());
    plan.setOrderType(req.getOrderType());
    plan.setTimeInForce(req.getTimeInForce());
    plan.setSymbols(normalizeSymbols(req.getSymbols()));
    plan.setMu(req.getMu());
    plan.setCov(req.getCov());
    plan.setCreatedAt(Instant.now());
    plan.setUpdatedAt(plan.getCreatedAt());

    ensureMinimumBalance(userId, plan.getMinimumBalance());
    validatePlan(plan);

    AutoInvestPlanEntity entity = toEntity(plan);
    plans.save(entity);

    audit.record(userId, userId, "AUTO_INVEST_PLAN_CREATED", "portfolio_auto_invest_plan", plan.getId(),
        Map.of("schedule", plan.getSchedule().name(), "symbols", plan.getSymbols().size()));

    return plan;
  }

  public List<AutoInvestPlan> listPlans(String userId) {
    String orgId = tenantContext.getOrgId();
    List<AutoInvestPlanEntity> rows = orgId == null
        ? plans.findByUserIdOrderByCreatedAtDesc(userId)
        : plans.findByUserIdAndOrgIdOrderByCreatedAtDesc(userId, orgId);
    return rows.stream()
        .map(this::toDto)
        .toList();
  }

  public AutoInvestPlan getPlan(String userId, String id) {
    AutoInvestPlanEntity entity = plans.findById(id).orElse(null);
    String orgId = tenantContext.getOrgId();
    if (entity == null || !userId.equals(entity.getUserId()) || (orgId != null && !orgId.equals(entity.getOrgId()))) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan not found");
    }
    return toDto(entity);
  }

  public AutoInvestPlan updateStatus(String userId, String id, AutoInvestPlanStatus status) {
    AutoInvestPlanEntity entity = plans.findById(id).orElse(null);
    String orgId = tenantContext.getOrgId();
    if (entity == null || !userId.equals(entity.getUserId()) || (orgId != null && !orgId.equals(entity.getOrgId()))) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan not found");
    }
    entity.setStatus(status.name());
    entity.setUpdatedAt(Instant.now());
    plans.save(entity);
    audit.record(userId, userId, "AUTO_INVEST_PLAN_STATUS", "portfolio_auto_invest_plan", id,
        Map.of("status", status.name()));
    return toDto(entity);
  }

  public List<AutoInvestRun> listRuns(String userId, String planId) {
    AutoInvestPlanEntity plan = plans.findById(planId).orElse(null);
    String orgId = tenantContext.getOrgId();
    if (plan == null || !userId.equals(plan.getUserId()) || (orgId != null && !orgId.equals(plan.getOrgId()))) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan not found");
    }
    return runs.findByPlanIdOrderByCreatedAtDesc(planId).stream()
        .map(this::toRunDto)
        .toList();
  }

  public List<com.alphamath.portfolio.domain.autoinvest.AutoInvestModelPortfolio> modelPortfolios() {
    List<com.alphamath.portfolio.domain.autoinvest.AutoInvestModelPortfolio> out = new ArrayList<>();

    com.alphamath.portfolio.domain.autoinvest.AutoInvestModelPortfolio conservative = new com.alphamath.portfolio.domain.autoinvest.AutoInvestModelPortfolio();
    conservative.setId("jpm_conservative");
    conservative.setName("JPM Conservative (Mock)");
    conservative.setRiskLevel("CONSERVATIVE");
    conservative.setDescription("Mock JPM-style conservative portfolio with higher fixed income allocation.");
    conservative.getAllocations().put("JPM_BOND_CORE", 0.55);
    conservative.getAllocations().put("JPM_BOND_SHORT", 0.20);
    conservative.getAllocations().put("JPM_EQ_US", 0.15);
    conservative.getAllocations().put("JPM_EQ_INTL", 0.05);
    conservative.getAllocations().put("JPM_CASH", 0.05);
    out.add(conservative);

    com.alphamath.portfolio.domain.autoinvest.AutoInvestModelPortfolio balanced = new com.alphamath.portfolio.domain.autoinvest.AutoInvestModelPortfolio();
    balanced.setId("jpm_balanced");
    balanced.setName("JPM Balanced (Mock)");
    balanced.setRiskLevel("BALANCED");
    balanced.setDescription("Mock JPM-style balanced portfolio with diversified equity and bond exposure.");
    balanced.getAllocations().put("JPM_EQ_US", 0.35);
    balanced.getAllocations().put("JPM_EQ_INTL", 0.20);
    balanced.getAllocations().put("JPM_BOND_CORE", 0.30);
    balanced.getAllocations().put("JPM_BOND_SHORT", 0.10);
    balanced.getAllocations().put("JPM_CASH", 0.05);
    out.add(balanced);

    com.alphamath.portfolio.domain.autoinvest.AutoInvestModelPortfolio growth = new com.alphamath.portfolio.domain.autoinvest.AutoInvestModelPortfolio();
    growth.setId("jpm_growth");
    growth.setName("JPM Growth (Mock)");
    growth.setRiskLevel("GROWTH");
    growth.setDescription("Mock JPM-style growth portfolio with equity tilt.");
    growth.getAllocations().put("JPM_EQ_US", 0.50);
    growth.getAllocations().put("JPM_EQ_INTL", 0.30);
    growth.getAllocations().put("JPM_BOND_CORE", 0.15);
    growth.getAllocations().put("JPM_CASH", 0.05);
    out.add(growth);

    return out;
  }

  public List<AutoInvestFee> listFees(String userId, String planId) {
    AutoInvestPlanEntity plan = plans.findById(planId).orElse(null);
    if (plan == null || !userId.equals(plan.getUserId())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan not found");
    }
    List<AutoInvestFee> out = new ArrayList<>();
    for (AutoInvestFeeEntity entity : fees.findByPlanIdOrderByCreatedAtDesc(planId, org.springframework.data.domain.PageRequest.of(0, 50))) {
      out.add(toFeeDto(entity));
    }
    return out;
  }

  public AutoInvestRun runNow(String userId, String planId) {
    AutoInvestPlanEntity planEntity = plans.findById(planId).orElse(null);
    if (planEntity == null || !userId.equals(planEntity.getUserId())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan not found");
    }
    AutoInvestPlan plan = toDto(planEntity);
    return executePlan(plan, AutoInvestTrigger.MANUAL, Instant.now());
  }

  public AutoInvestFee chargeFeeNow(String userId, String planId) {
    AutoInvestPlanEntity planEntity = plans.findById(planId).orElse(null);
    if (planEntity == null || !userId.equals(planEntity.getUserId())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan not found");
    }
    AutoInvestPlan plan = toDto(planEntity);
    return chargeFee(plan, Instant.now());
  }

  public void runScheduled() {
    Instant now = Instant.now();
    for (AutoInvestPlanEntity entity : plans.findByStatusOrderByCreatedAtAsc(AutoInvestPlanStatus.ACTIVE.name())) {
      AutoInvestPlan plan = toDto(entity);
      if (!isDue(plan, now)) {
        continue;
      }
      AutoInvestTrigger trigger = plan.getSchedule() == AutoInvestSchedule.DRIFT
          ? AutoInvestTrigger.DRIFT
          : AutoInvestTrigger.SCHEDULED;
      executePlan(plan, trigger, now);
    }
  }

  public void runAdvisoryFees() {
    Instant now = Instant.now();
    for (AutoInvestPlanEntity entity : plans.findByStatusOrderByCreatedAtAsc(AutoInvestPlanStatus.ACTIVE.name())) {
      AutoInvestPlan plan = toDto(entity);
      if (shouldChargeFee(plan, now)) {
        chargeFee(plan, now);
      }
    }
  }

  private AutoInvestRun executePlan(AutoInvestPlan plan, AutoInvestTrigger trigger, Instant now) {
    String idempotencyKey = buildIdempotencyKey(plan, trigger, now);
    if (runs.findByIdempotencyKey(idempotencyKey).isPresent()) {
      return null;
    }
    AutoInvestRunEntity run = new AutoInvestRunEntity();
    run.setId(UUID.randomUUID().toString());
    run.setPlanId(plan.getId());
    run.setUserId(plan.getUserId());
    run.setOrgId(plan.getOrgId() == null ? tenantContext.getOrgId() : plan.getOrgId());
    run.setTrigger(trigger.name());
    run.setStatus(AutoInvestRunStatus.PENDING.name());
    run.setIdempotencyKey(idempotencyKey);
    run.setCreatedAt(now);
    run.setUpdatedAt(now);

    try {
      runs.save(run);
    } catch (DataIntegrityViolationException e) {
      return null;
    }

    try {
      RunInputs inputs = buildInputs(plan);
      if (inputs.totalEquity <= 0.0) {
        return failRun(run, "Account equity must be positive", Map.of("equity", inputs.totalEquity));
      }

      double drift = computeDrift(inputs.currentWeights, inputs.targetWeights);
      Map<String, Object> metrics = new LinkedHashMap<>();
      metrics.put("drift", drift);
      metrics.put("equity", inputs.totalEquity);
      metrics.put("cash", inputs.cash);
      metrics.put("returnsPoints", inputs.returnPoints);
      metrics.put("symbols", inputs.symbols);
      metrics.put("aiForecast", inputs.aiUsed);
      metrics.put("quoteSource", inputs.quoteSources);
      metrics.put("currentWeights", inputs.currentWeights);
      metrics.put("targetWeights", toList(inputs.targetWeights));

      Double driftThreshold = plan.getDriftThreshold();
      if (driftThreshold != null && driftThreshold > 0 && drift < driftThreshold) {
        run.setStatus(AutoInvestRunStatus.SKIPPED.name());
        run.setReason("Drift below threshold");
        run.setMetricsJson(JsonUtils.toJson(metrics));
        run.setUpdatedAt(Instant.now());
        runs.save(run);
        updatePlanLastRun(plan, now);
        if (trigger == AutoInvestTrigger.MANUAL) {
          notifications.create(plan.getUserId(), NotificationType.AUTO_INVEST_SKIPPED,
              "Auto-invest skipped", "Drift below threshold for " + plan.getName(),
              "portfolio_auto_invest_plan", plan.getId(), metrics);
        }
        return toRunDto(run);
      }

      TradeProposalRequest req = inputs.request;
      TradeProposal proposal = trade.createProposal(plan.getUserId(), req);
      run.setProposalId(proposal.getId());
      run.setStatus(AutoInvestRunStatus.PROPOSAL_CREATED.name());
      run.setMetricsJson(JsonUtils.toJson(metrics));
      run.setUpdatedAt(Instant.now());
      runs.save(run);
      updatePlanLastRun(plan, now);

      notifications.create(plan.getUserId(), NotificationType.AUTO_INVEST_PROPOSAL,
          "Auto-invest proposal ready",
          "Proposal ready for plan " + plan.getName() + ". Approval required.",
          "portfolio_trade_proposal", proposal.getId(),
          Map.of("planId", plan.getId(), "runId", run.getId()));

      audit.record(plan.getUserId(), "system", "AUTO_INVEST_PROPOSAL_CREATED",
          "portfolio_auto_invest_run", run.getId(),
          Map.of("proposalId", proposal.getId(), "trigger", trigger.name()));

      return toRunDto(run);
    } catch (Exception e) {
      return failRun(run, e.getMessage(), Map.of());
    }
  }

  private AutoInvestRun failRun(AutoInvestRunEntity run, String reason, Map<String, Object> metrics) {
    run.setStatus(AutoInvestRunStatus.FAILED.name());
    run.setReason(reason == null || reason.isBlank() ? "Auto-invest failed" : reason);
    run.setMetricsJson(JsonUtils.toJson(metrics));
    run.setUpdatedAt(Instant.now());
    runs.save(run);

    notifications.create(run.getUserId(), NotificationType.AUTO_INVEST_FAILED,
        "Auto-invest failed", run.getReason(),
        "portfolio_auto_invest_run", run.getId(), metrics);

    audit.record(run.getUserId(), "system", "AUTO_INVEST_FAILED",
        "portfolio_auto_invest_run", run.getId(),
        Map.of("reason", run.getReason()));

    return toRunDto(run);
  }

  private RunInputs buildInputs(AutoInvestPlan plan) {
    List<String> symbols = plan.getSymbols();
    LatestQuotesResult quoteResult = marketData.latestQuotes(symbols);
    if (!quoteResult.missing().isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing quotes for: " + String.join(",", quoteResult.missing()));
    }

    Map<String, Double> prices = new LinkedHashMap<>();
    Map<String, String> sources = new LinkedHashMap<>();
    for (LatestQuotesResult.QuoteSnapshot snapshot : quoteResult.quotes()) {
      MarketQuote quote = snapshot.quote();
      prices.put(quote.symbol(), quote.price());
      sources.put(quote.symbol(), quote.source());
    }

    List<List<Double>> returns = new ArrayList<>();
    int lookback = plan.getReturnsLookback() == null ? defaultLookback : plan.getReturnsLookback();
    int minLen = Integer.MAX_VALUE;
    for (String symbol : symbols) {
      List<Double> series = marketData.returns(symbol, null, null, lookback);
      returns.add(series);
      minLen = Math.min(minLen, series.size());
    }

    double[] mu;
    double[][] cov;
    if (!plan.isUseMarketData()) {
      mu = toVector(plan.getMu());
      cov = toMatrix(plan.getCov(), mu.length);
    } else {
      if (minLen < minReturns) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
            "Not enough returns data to compute forecasts (need " + minReturns + ")");
      }
      mu = new double[symbols.size()];
      cov = new double[symbols.size()][symbols.size()];
      List<List<Double>> aligned = alignReturns(returns, minLen);
      for (int i = 0; i < symbols.size(); i++) {
        mu[i] = mean(aligned.get(i));
      }
      for (int i = 0; i < symbols.size(); i++) {
        for (int j = 0; j < symbols.size(); j++) {
          cov[i][j] = covariance(aligned.get(i), aligned.get(j), mu[i], mu[j]);
        }
      }
    }

    TradeProposalRequest req = new TradeProposalRequest();
    req.setSymbols(symbols);
    req.setMu(toList(mu));
    req.setCov(toListMatrix(cov));
    req.setPrices(prices);
    if (plan.getMethod() == null) {
      plan.setMethod(com.alphamath.portfolio.math.Optimizers.Method.MEAN_VARIANCE_PGD);
    }
    req.setMethod(plan.getMethod());
    req.setRiskAversion(plan.getRiskAversion());
    req.setMaxWeight(plan.getMaxWeight());
    req.setMinWeight(plan.getMinWeight());
    req.setMinTradeValue(plan.getMinTradeValue());
    req.setMaxTradePctOfEquity(plan.getMaxTradePctOfEquity());
    req.setMaxTurnover(plan.getMaxTurnover());
    req.setExecutionMode(plan.getExecutionMode());
    req.setRegion(plan.getRegion());
    req.setAssetClass(plan.getAssetClass());
    req.setProviderPreference(plan.getProviderPreference());
    req.setOrderType(plan.getOrderType());
    req.setTimeInForce(plan.getTimeInForce());

    List<Double> portfolioReturns = plan.isUseAiForecast() ? buildPortfolioReturns(returns, minLen) : List.of();
    if (!portfolioReturns.isEmpty()) {
      req.setReturns(portfolioReturns);
      req.setAiHorizon(plan.getAiHorizon());
    }

    PaperAccount account = trade.getAccount(plan.getUserId());
    Map<String, Double> currentWeights = computeCurrentWeights(symbols, account, prices);
    double[] targetWeights = computeTargetWeights(req, mu, cov);

    RunInputs inputs = new RunInputs();
    inputs.symbols = symbols.size();
    inputs.request = req;
    inputs.currentWeights = currentWeights;
    inputs.targetWeights = targetWeights;
    inputs.cash = account.getCash();
    inputs.totalEquity = computeTotalEquity(symbols, account, prices);
    inputs.returnPoints = plan.isUseMarketData() ? minLen : 0;
    inputs.aiUsed = plan.isUseAiForecast() && !portfolioReturns.isEmpty();
    inputs.quoteSources = sources;
    return inputs;
  }

  private Map<String, Double> computeCurrentWeights(List<String> symbols, PaperAccount account,
                                                    Map<String, Double> prices) {
    Map<String, Double> weights = new LinkedHashMap<>();
    double total = computeTotalEquity(symbols, account, prices);
    if (total <= 0.0) {
      return weights;
    }
    for (String symbol : symbols) {
      double qty = account.getPositions().getOrDefault(symbol, 0.0);
      double value = qty * prices.get(symbol);
      weights.put(symbol, value / total);
    }
    return weights;
  }

  private double computeTotalEquity(List<String> symbols, PaperAccount account, Map<String, Double> prices) {
    double total = account.getCash();
    for (String symbol : symbols) {
      double qty = account.getPositions().getOrDefault(symbol, 0.0);
      total += qty * prices.get(symbol);
    }
    return total;
  }

  private double[] computeTargetWeights(TradeProposalRequest req, double[] mu, double[][] cov) {
    var method = req.getMethod() == null
        ? com.alphamath.portfolio.math.Optimizers.Method.MEAN_VARIANCE_PGD
        : req.getMethod();
    return switch (method) {
      case MIN_VARIANCE -> com.alphamath.portfolio.math.Optimizers.optimizeMinVariance(
          cov, req.getMinWeight(), req.getMaxWeight(), 4000, 0.02);
      case RISK_PARITY -> com.alphamath.portfolio.math.Optimizers.optimizeRiskParity(
          cov, req.getMinWeight(), req.getMaxWeight(), 4000);
      case KELLY_APPROX -> com.alphamath.portfolio.math.Optimizers.optimizeKellyApprox(
          mu, cov,
          req.getFractionalKelly() == null ? 0.25 : req.getFractionalKelly(),
          req.getMinWeight(), req.getMaxWeight());
      default -> com.alphamath.portfolio.math.Optimizers.optimizeMeanVariancePGD(
          mu, cov, req.getRiskAversion(), req.getMinWeight(), req.getMaxWeight(), 4000, 0.02);
    };
  }

  private double computeDrift(Map<String, Double> current, double[] target) {
    if (current.isEmpty() || target.length == 0) return 0.0;
    double drift = 0.0;
    int i = 0;
    for (Double weight : current.values()) {
      if (i >= target.length) break;
      drift += Math.abs(weight - target[i]);
      i += 1;
    }
    return drift;
  }

  private List<List<Double>> alignReturns(List<List<Double>> returns, int len) {
    List<List<Double>> out = new ArrayList<>();
    for (List<Double> series : returns) {
      if (series.size() <= len) {
        out.add(series);
      } else {
        out.add(series.subList(series.size() - len, series.size()));
      }
    }
    return out;
  }

  private List<Double> buildPortfolioReturns(List<List<Double>> returns, int len) {
    if (returns.isEmpty() || len <= 0) return List.of();
    List<List<Double>> aligned = alignReturns(returns, len);
    List<Double> out = new ArrayList<>();
    for (int i = 0; i < len; i++) {
      double sum = 0.0;
      for (List<Double> series : aligned) {
        sum += series.get(i);
      }
      out.add(sum / aligned.size());
    }
    return out;
  }

  private double mean(List<Double> series) {
    double sum = 0.0;
    for (double v : series) sum += v;
    return sum / Math.max(1, series.size());
  }

  private double covariance(List<Double> a, List<Double> b, double ma, double mb) {
    int n = Math.min(a.size(), b.size());
    if (n <= 1) return 0.0;
    double sum = 0.0;
    for (int i = 0; i < n; i++) {
      sum += (a.get(i) - ma) * (b.get(i) - mb);
    }
    return sum / (n - 1);
  }

  private double[] toVector(List<Double> list) {
    double[] out = new double[list.size()];
    for (int i = 0; i < list.size(); i++) out[i] = list.get(i);
    return out;
  }

  private double[][] toMatrix(List<List<Double>> list, int n) {
    double[][] out = new double[n][n];
    for (int i = 0; i < n; i++) {
      List<Double> row = list.get(i);
      for (int j = 0; j < n; j++) {
        out[i][j] = row.get(j);
      }
    }
    return out;
  }

  private List<Double> toList(double[] vec) {
    List<Double> out = new ArrayList<>();
    for (double v : vec) out.add(v);
    return out;
  }

  private List<List<Double>> toListMatrix(double[][] matrix) {
    List<List<Double>> out = new ArrayList<>();
    for (double[] row : matrix) {
      List<Double> r = new ArrayList<>();
      for (double v : row) r.add(v);
      out.add(r);
    }
    return out;
  }

  private void updatePlanLastRun(AutoInvestPlan plan, Instant now) {
    AutoInvestPlanEntity entity = plans.findById(plan.getId()).orElse(null);
    if (entity == null) return;
    entity.setLastRunAt(now);
    entity.setUpdatedAt(now);
    plans.save(entity);
  }

  private boolean isDue(AutoInvestPlan plan, Instant now) {
    if (plan.getSchedule() == null) return false;
    if (plan.getSchedule() == AutoInvestSchedule.DRIFT) {
      return true;
    }

    if (!isScheduledDay(plan, now)) {
      return false;
    }
    if (!isScheduledTime(plan, now)) {
      return false;
    }

    if (plan.getLastRunAt() == null) {
      return true;
    }
    LocalDate last = LocalDate.ofInstant(plan.getLastRunAt(), ZoneOffset.UTC);
    LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);

    return switch (plan.getSchedule()) {
      case DAILY -> last.isBefore(today);
      case WEEKLY -> !sameWeek(last, today);
      default -> false;
    };
  }

  private boolean isScheduledDay(AutoInvestPlan plan, Instant now) {
    if (plan.getSchedule() != AutoInvestSchedule.WEEKLY) {
      return true;
    }
    String raw = plan.getScheduleDayOfWeek();
    if (raw == null || raw.isBlank()) {
      return true;
    }
    DayOfWeek target;
    try {
      target = DayOfWeek.valueOf(raw.trim().toUpperCase(Locale.US));
    } catch (IllegalArgumentException e) {
      return true;
    }
    DayOfWeek current = LocalDate.ofInstant(now, ZoneOffset.UTC).getDayOfWeek();
    return current == target;
  }

  private boolean isScheduledTime(AutoInvestPlan plan, Instant now) {
    String raw = plan.getScheduleTimeUtc();
    if (raw == null || raw.isBlank()) {
      return true;
    }
    LocalTime target;
    try {
      target = LocalTime.parse(raw.trim());
    } catch (Exception e) {
      return true;
    }
    LocalTime current = LocalTime.ofInstant(now, ZoneOffset.UTC);
    return !current.isBefore(target);
  }

  private boolean sameWeek(LocalDate a, LocalDate b) {
    WeekFields wf = WeekFields.ISO;
    return a.get(wf.weekBasedYear()) == b.get(wf.weekBasedYear())
        && a.get(wf.weekOfWeekBasedYear()) == b.get(wf.weekOfWeekBasedYear());
  }

  private String buildIdempotencyKey(AutoInvestPlan plan, AutoInvestTrigger trigger, Instant now) {
    if (trigger == AutoInvestTrigger.MANUAL) {
      return plan.getId() + "|MANUAL|" + UUID.randomUUID();
    }
    if (plan.getSchedule() == AutoInvestSchedule.DRIFT) {
      long window = Math.max(1, driftIdempotencyHours);
      long bucket = now.getEpochSecond() / (window * 3600L);
      return plan.getId() + "|DRIFT|" + bucket;
    }
    LocalDate date = LocalDate.ofInstant(now, ZoneOffset.UTC);
    if (plan.getSchedule() == AutoInvestSchedule.WEEKLY) {
      WeekFields wf = WeekFields.ISO;
      int week = date.get(wf.weekOfWeekBasedYear());
      int year = date.get(wf.weekBasedYear());
      return plan.getId() + "|WEEKLY|" + year + "-W" + week;
    }
    return plan.getId() + "|DAILY|" + date;
  }

  private List<String> normalizeSymbols(List<String> symbols) {
    if (symbols == null || symbols.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "symbols are required");
    }
    Map<String, Boolean> uniq = new LinkedHashMap<>();
    for (String symbol : symbols) {
      if (symbol == null || symbol.isBlank()) continue;
      uniq.putIfAbsent(symbol.trim().toUpperCase(Locale.US), true);
    }
    if (uniq.size() < 2) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "at least two symbols are required");
    }
    return new ArrayList<>(uniq.keySet());
  }

  private void validatePlan(AutoInvestPlan plan) {
    providerPolicy.enforceAssetClass(plan.getProviderPreference(), plan.getAssetClass());
    if (!plan.isUseMarketData()) {
      int n = plan.getSymbols().size();
      if (plan.getMu() == null || plan.getMu().size() != n) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "mu length must match symbols");
      }
      if (plan.getCov() == null || plan.getCov().size() != n) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cov must be NxN");
      }
      for (var row : plan.getCov()) {
        if (row.size() != n) {
          throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cov must be NxN");
        }
      }
    }
  }

  private void ensureMinimumBalance(String userId, Double minimumBalance) {
    if (minimumBalance == null || minimumBalance <= 0.0) return;
    EquitySnapshot equity = computeEquitySnapshot(userId);
    if (equity.totalEquity + 1e-9 < minimumBalance) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "minimum balance $" + round(minimumBalance, 2) + " required (current $" + round(equity.totalEquity, 2) + ")");
    }
  }

  private boolean shouldChargeFee(AutoInvestPlan plan, Instant now) {
    double feeBps = plan.getAdvisoryFeeBpsAnnual() == null ? advisoryFeeBpsAnnualDefault : plan.getAdvisoryFeeBpsAnnual();
    if (feeBps <= 0.0) return false;
    Instant last = plan.getLastFeeChargedAt();
    if (last == null) return true;
    long days = ChronoUnit.DAYS.between(last, now);
    return days >= advisoryFeeChargeDays;
  }

  private AutoInvestFee chargeFee(AutoInvestPlan plan, Instant now) {
    EquitySnapshot equity = computeEquitySnapshot(plan.getUserId());
    double feeBps = plan.getAdvisoryFeeBpsAnnual() == null ? advisoryFeeBpsAnnualDefault : plan.getAdvisoryFeeBpsAnnual();
    double feeRate = feeBps / 10000.0;
    double charge = equity.totalEquity * feeRate * (advisoryFeeChargeDays / 365.0);

    AutoInvestFeeEntity entity = new AutoInvestFeeEntity();
    entity.setId(UUID.randomUUID().toString());
    entity.setPlanId(plan.getId());
    entity.setUserId(plan.getUserId());
    entity.setOrgId(plan.getOrgId() == null ? tenantContext.getOrgId() : plan.getOrgId());
    entity.setEquity(equity.totalEquity);
    entity.setFeeBpsAnnual(feeBps);
    entity.setChargeDays(advisoryFeeChargeDays);
    entity.setAmount(Math.max(0.0, charge));
    entity.setCreatedAt(now);

    if (charge <= 0.0) {
      entity.setStatus("SKIPPED_ZERO");
      fees.save(entity);
      return toFeeDto(entity);
    }

    try {
      trade.debitCash(plan.getUserId(), charge);
      entity.setStatus("CHARGED");
      plan.setLastFeeChargedAt(now);
      plan.setUpdatedAt(now);
      plans.save(toEntity(plan));
      fees.save(entity);
      notifications.create(plan.getUserId(), NotificationType.AUTO_INVEST_FEE,
          "Advisory fee charged",
          "Auto-invest advisory fee of $" + round(charge, 2) + " charged for " + plan.getName(),
          "portfolio_auto_invest_plan", plan.getId(), java.util.Map.of(
              "feeBpsAnnual", feeBps,
              "equity", equity.totalEquity,
              "chargeDays", advisoryFeeChargeDays
          ));
      audit.record(plan.getUserId(), "system", "AUTO_INVEST_FEE",
          "portfolio_auto_invest_plan", plan.getId(), java.util.Map.of(
              "amount", charge,
              "feeBpsAnnual", feeBps,
              "equity", equity.totalEquity
          ));
    } catch (ResponseStatusException e) {
      entity.setStatus("SKIPPED_INSUFFICIENT_CASH");
      fees.save(entity);
    }

    return toFeeDto(entity);
  }

  private EquitySnapshot computeEquitySnapshot(String userId) {
    PaperAccount account = trade.getAccount(userId);
    double total = account.getCash();
    if (account.getPositions() == null || account.getPositions().isEmpty()) {
      return new EquitySnapshot(total, List.of());
    }
    List<String> symbols = new ArrayList<>(account.getPositions().keySet());
    LatestQuotesResult quotes = marketData.latestQuotes(symbols);
    Map<String, MarketQuote> quoteMap = new LinkedHashMap<>();
    for (LatestQuotesResult.QuoteSnapshot snapshot : quotes.quotes()) {
      quoteMap.put(snapshot.quote().symbol(), snapshot.quote());
    }
    List<String> missing = new ArrayList<>();
    for (String symbol : symbols) {
      MarketQuote quote = quoteMap.get(symbol);
      if (quote == null || quote.price() <= 0.0) {
        missing.add(symbol);
        continue;
      }
      double qty = account.getPositions().getOrDefault(symbol, 0.0);
      total += qty * quote.price();
    }
    return new EquitySnapshot(total, missing);
  }

  private AutoInvestPlanEntity toEntity(AutoInvestPlan plan) {
    AutoInvestPlanEntity entity = new AutoInvestPlanEntity();
    entity.setId(plan.getId());
    entity.setUserId(plan.getUserId());
    entity.setOrgId(plan.getOrgId() == null ? tenantContext.getOrgId() : plan.getOrgId());
    entity.setName(plan.getName());
    entity.setStatus(plan.getStatus().name());
    entity.setSchedule(plan.getSchedule().name());
    entity.setGoalType(plan.getGoalType() == null ? null : plan.getGoalType().name());
    entity.setScheduleTimeUtc(plan.getScheduleTimeUtc());
    entity.setScheduleDayOfWeek(plan.getScheduleDayOfWeek());
    entity.setDriftThreshold(plan.getDriftThreshold());
    entity.setReturnsLookback(plan.getReturnsLookback());
    entity.setUseMarketData(plan.isUseMarketData());
    entity.setUseAiForecast(plan.isUseAiForecast());
    entity.setAiHorizon(plan.getAiHorizon());
    entity.setMethod(plan.getMethod());
    entity.setRiskAversion(plan.getRiskAversion());
    entity.setMaxWeight(plan.getMaxWeight());
    entity.setMinWeight(plan.getMinWeight());
    entity.setMinTradeValue(plan.getMinTradeValue());
    entity.setMaxTradePct(plan.getMaxTradePctOfEquity());
    entity.setMaxTurnover(plan.getMaxTurnover());
    entity.setAdvisoryFeeBpsAnnual(plan.getAdvisoryFeeBpsAnnual());
    entity.setMinimumBalance(plan.getMinimumBalance());
    entity.setExecutionMode(plan.getExecutionMode());
    entity.setRegion(plan.getRegion());
    entity.setAssetClass(plan.getAssetClass());
    entity.setProviderPreference(plan.getProviderPreference());
    entity.setOrderType(plan.getOrderType());
    entity.setTimeInForce(plan.getTimeInForce());
    entity.setSymbolsJson(JsonUtils.toJson(plan.getSymbols()));
    entity.setMuJson(plan.getMu() == null ? null : JsonUtils.toJson(plan.getMu()));
    entity.setCovJson(plan.getCov() == null ? null : JsonUtils.toJson(plan.getCov()));
    entity.setCreatedAt(plan.getCreatedAt());
    entity.setUpdatedAt(plan.getUpdatedAt());
    entity.setLastRunAt(plan.getLastRunAt());
    entity.setLastFeeChargedAt(plan.getLastFeeChargedAt());
    return entity;
  }

  private AutoInvestPlan toDto(AutoInvestPlanEntity entity) {
    AutoInvestPlan plan = new AutoInvestPlan();
    plan.setId(entity.getId());
    plan.setUserId(entity.getUserId());
    plan.setOrgId(entity.getOrgId());
    plan.setName(entity.getName());
    plan.setStatus(AutoInvestPlanStatus.valueOf(entity.getStatus()));
    plan.setSchedule(AutoInvestSchedule.valueOf(entity.getSchedule()));
    plan.setGoalType(entity.getGoalType() == null ? AutoInvestGoalType.GENERAL_INVESTING : AutoInvestGoalType.valueOf(entity.getGoalType()));
    plan.setScheduleTimeUtc(entity.getScheduleTimeUtc());
    plan.setScheduleDayOfWeek(entity.getScheduleDayOfWeek());
    plan.setDriftThreshold(entity.getDriftThreshold());
    plan.setReturnsLookback(entity.getReturnsLookback());
    plan.setUseMarketData(entity.isUseMarketData());
    plan.setUseAiForecast(entity.isUseAiForecast());
    plan.setAiHorizon(entity.getAiHorizon());
    plan.setMethod(entity.getMethod());
    plan.setRiskAversion(entity.getRiskAversion());
    plan.setMaxWeight(entity.getMaxWeight());
    plan.setMinWeight(entity.getMinWeight());
    plan.setMinTradeValue(entity.getMinTradeValue());
    plan.setMaxTradePctOfEquity(entity.getMaxTradePct());
    plan.setMaxTurnover(entity.getMaxTurnover());
    plan.setAdvisoryFeeBpsAnnual(entity.getAdvisoryFeeBpsAnnual());
    plan.setMinimumBalance(entity.getMinimumBalance());
    plan.setExecutionMode(entity.getExecutionMode());
    plan.setRegion(entity.getRegion());
    plan.setAssetClass(entity.getAssetClass());
    plan.setProviderPreference(entity.getProviderPreference());
    plan.setOrderType(entity.getOrderType());
    plan.setTimeInForce(entity.getTimeInForce());
    plan.setSymbols(parseList(entity.getSymbolsJson()));
    plan.setMu(parseDoubleList(entity.getMuJson()));
    plan.setCov(parseDoubleMatrix(entity.getCovJson()));
    plan.setCreatedAt(entity.getCreatedAt());
    plan.setUpdatedAt(entity.getUpdatedAt());
    plan.setLastRunAt(entity.getLastRunAt());
    plan.setLastFeeChargedAt(entity.getLastFeeChargedAt());
    return plan;
  }

  private AutoInvestRun toRunDto(AutoInvestRunEntity entity) {
    AutoInvestRun run = new AutoInvestRun();
    run.setId(entity.getId());
    run.setPlanId(entity.getPlanId());
    run.setUserId(entity.getUserId());
    run.setTrigger(AutoInvestTrigger.valueOf(entity.getTrigger()));
    run.setStatus(AutoInvestRunStatus.valueOf(entity.getStatus()));
    run.setIdempotencyKey(entity.getIdempotencyKey());
    run.setProposalId(entity.getProposalId());
    run.setReason(entity.getReason());
    run.setMetrics(parseMetrics(entity.getMetricsJson()));
    run.setCreatedAt(entity.getCreatedAt());
    run.setUpdatedAt(entity.getUpdatedAt());
    return run;
  }

  private AutoInvestFee toFeeDto(AutoInvestFeeEntity entity) {
    AutoInvestFee fee = new AutoInvestFee();
    fee.setId(entity.getId());
    fee.setPlanId(entity.getPlanId());
    fee.setUserId(entity.getUserId());
    fee.setAmount(entity.getAmount());
    fee.setEquity(entity.getEquity());
    fee.setFeeBpsAnnual(entity.getFeeBpsAnnual());
    fee.setChargeDays(entity.getChargeDays());
    fee.setStatus(entity.getStatus());
    fee.setCreatedAt(entity.getCreatedAt());
    return fee;
  }

  private List<String> parseList(String json) {
    if (json == null || json.isBlank()) return new ArrayList<>();
    try {
      return JsonUtils.fromJson(json, new TypeReference<List<String>>() {});
    } catch (Exception e) {
      return new ArrayList<>();
    }
  }

  private List<Double> parseDoubleList(String json) {
    if (json == null || json.isBlank()) return new ArrayList<>();
    try {
      return JsonUtils.fromJson(json, new TypeReference<List<Double>>() {});
    } catch (Exception e) {
      return new ArrayList<>();
    }
  }

  private List<List<Double>> parseDoubleMatrix(String json) {
    if (json == null || json.isBlank()) return new ArrayList<>();
    try {
      return JsonUtils.fromJson(json, new TypeReference<List<List<Double>>>() {});
    } catch (Exception e) {
      return new ArrayList<>();
    }
  }

  private Map<String, Object> parseMetrics(String json) {
    if (json == null || json.isBlank()) return new LinkedHashMap<>();
    try {
      return JsonUtils.fromJson(json, new TypeReference<Map<String, Object>>() {});
    } catch (Exception e) {
      return new LinkedHashMap<>();
    }
  }

  private double round(Double value, int scale) {
    double safe = value == null ? 0.0 : value;
    double factor = Math.pow(10.0, Math.max(0, scale));
    return Math.round(safe * factor) / factor;
  }

  private static class RunInputs {
    TradeProposalRequest request;
    Map<String, Double> currentWeights;
    double[] targetWeights;
    double totalEquity;
    double cash;
    int returnPoints;
    boolean aiUsed;
    int symbols;
    Map<String, String> quoteSources;
  }

  private static class EquitySnapshot {
    private final double totalEquity;
    private final List<String> missingSymbols;

    private EquitySnapshot(double totalEquity, List<String> missingSymbols) {
      this.totalEquity = totalEquity;
      this.missingSymbols = missingSymbols;
    }
  }
}
