package com.alphamath.portfolio.application.wealth;

import com.alphamath.portfolio.domain.wealth.WealthPlan;
import com.alphamath.portfolio.domain.wealth.WealthPlanRequest;
import com.alphamath.portfolio.domain.wealth.WealthPlanSimulationRequest;
import com.alphamath.portfolio.domain.wealth.WealthPlanSimulationResult;
import com.alphamath.portfolio.domain.wealth.WealthPlanType;
import com.alphamath.portfolio.infrastructure.persistence.WealthPlanEntity;
import com.alphamath.portfolio.infrastructure.persistence.WealthPlanRepository;
import com.alphamath.portfolio.security.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@Service
public class WealthPlanService {
  private final WealthPlanRepository plans;
  private final TenantContext tenantContext;
  private final int defaultSimulations;
  private final double defaultReturn;
  private final double defaultVolatility;

  public WealthPlanService(WealthPlanRepository plans,
                           TenantContext tenantContext,
                           @Value("${alphamath.wealthplan.simulations:1000}") int defaultSimulations,
                           @Value("${alphamath.wealthplan.defaultReturn:0.06}") double defaultReturn,
                           @Value("${alphamath.wealthplan.defaultVolatility:0.12}") double defaultVolatility) {
    this.plans = plans;
    this.tenantContext = tenantContext;
    this.defaultSimulations = Math.max(200, defaultSimulations);
    this.defaultReturn = Math.max(0.0, defaultReturn);
    this.defaultVolatility = Math.max(0.0, defaultVolatility);
  }

  public WealthPlan create(String userId, WealthPlanRequest req) {
    if (req == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request required");
    }
    WealthPlan plan = new WealthPlan();
    plan.setId(UUID.randomUUID().toString());
    plan.setUserId(userId);
    plan.setPlanType(req.getPlanType() == null ? WealthPlanType.GENERAL_INVESTING : req.getPlanType());
    plan.setName(req.getName() == null || req.getName().isBlank() ? "Wealth Plan" : req.getName().trim());
    plan.setStartingBalance(req.getStartingBalance() == null ? 0.0 : req.getStartingBalance());
    plan.setTargetBalance(req.getTargetBalance() == null ? 0.0 : req.getTargetBalance());
    plan.setMonthlyContribution(req.getMonthlyContribution() == null ? 0.0 : req.getMonthlyContribution());
    plan.setHorizonYears(req.getHorizonYears() == null ? 1 : req.getHorizonYears());
    plan.setExpectedReturn(req.getExpectedReturn());
    plan.setVolatility(req.getVolatility());
    plan.setSimulationCount(req.getSimulationCount());
    plan.setCreatedAt(Instant.now());
    plan.setUpdatedAt(plan.getCreatedAt());

    validate(plan);

    WealthPlanSimulationResult sim = simulateInternal(plan, null);
    applySimulation(plan, sim);

    plans.save(toEntity(plan));
    return plan;
  }

  public List<WealthPlan> list(String userId) {
    List<WealthPlan> out = new ArrayList<>();
    String orgId = tenantContext.getOrgId();
    List<WealthPlanEntity> rows = orgId == null
        ? plans.findByUserIdOrderByCreatedAtDesc(userId)
        : plans.findByUserIdAndOrgIdOrderByCreatedAtDesc(userId, orgId);
    for (WealthPlanEntity entity : rows) {
      out.add(toDto(entity));
    }
    return out;
  }

  public WealthPlan get(String userId, String id) {
    WealthPlanEntity entity = plans.findById(id).orElse(null);
    String orgId = tenantContext.getOrgId();
    if (entity == null || !userId.equals(entity.getUserId()) || (orgId != null && !orgId.equals(entity.getOrgId()))) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan not found");
    }
    return toDto(entity);
  }

  public WealthPlanSimulationResult simulate(String userId, String id, WealthPlanSimulationRequest req) {
    WealthPlanEntity entity = plans.findById(id).orElse(null);
    String orgId = tenantContext.getOrgId();
    if (entity == null || !userId.equals(entity.getUserId()) || (orgId != null && !orgId.equals(entity.getOrgId()))) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan not found");
    }
    WealthPlan plan = toDto(entity);
    WealthPlanSimulationResult sim = simulateInternal(plan, req);
    applySimulation(plan, sim);
    plans.save(toEntity(plan));
    return sim;
  }

  private void applySimulation(WealthPlan plan, WealthPlanSimulationResult sim) {
    plan.setSimulationCount(sim.getSimulationCount());
    plan.setSuccessProbability(sim.getSuccessProbability());
    plan.setMedianOutcome(sim.getMedianOutcome());
    plan.setP10Outcome(sim.getP10Outcome());
    plan.setP90Outcome(sim.getP90Outcome());
    plan.setLastSimulatedAt(Instant.now());
    plan.setUpdatedAt(Instant.now());
  }

  private WealthPlanSimulationResult simulateInternal(WealthPlan plan, WealthPlanSimulationRequest req) {
    int sims = req != null && req.getSimulationCount() != null ? req.getSimulationCount() :
        (plan.getSimulationCount() != null ? plan.getSimulationCount() : defaultSimulations);
    sims = Math.max(200, sims);
    double expectedReturn = req != null && req.getExpectedReturn() != null ? req.getExpectedReturn() :
        (plan.getExpectedReturn() != null ? plan.getExpectedReturn() : defaultReturn);
    double volatility = req != null && req.getVolatility() != null ? req.getVolatility() :
        (plan.getVolatility() != null ? plan.getVolatility() : defaultVolatility);

    int months = Math.max(1, plan.getHorizonYears()) * 12;
    double monthlyMu = expectedReturn / 12.0;
    double monthlySigma = volatility / Math.sqrt(12.0);

    Random random = new Random(plan.getId().hashCode());
    List<Double> outcomes = new ArrayList<>(sims);
    int success = 0;

    for (int i = 0; i < sims; i++) {
      double value = plan.getStartingBalance();
      for (int m = 0; m < months; m++) {
        double shock = random.nextGaussian();
        double r = monthlyMu + monthlySigma * shock;
        value = value * (1.0 + r) + plan.getMonthlyContribution();
      }
      outcomes.add(value);
      if (value >= plan.getTargetBalance()) {
        success++;
      }
    }

    Collections.sort(outcomes);
    WealthPlanSimulationResult result = new WealthPlanSimulationResult();
    result.setPlanId(plan.getId());
    result.setSimulationCount(sims);
    result.setSuccessProbability(success / (double) sims);
    result.setMedianOutcome(percentile(outcomes, 0.5));
    result.setP10Outcome(percentile(outcomes, 0.1));
    result.setP90Outcome(percentile(outcomes, 0.9));
    return result;
  }

  private double percentile(List<Double> values, double p) {
    if (values.isEmpty()) return 0.0;
    int index = (int) Math.round((values.size() - 1) * p);
    index = Math.max(0, Math.min(values.size() - 1, index));
    return values.get(index);
  }

  private void validate(WealthPlan plan) {
    if (plan.getStartingBalance() < 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startingBalance must be >= 0");
    }
    if (plan.getTargetBalance() <= 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "targetBalance must be > 0");
    }
    if (plan.getMonthlyContribution() < 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "monthlyContribution must be >= 0");
    }
    if (plan.getHorizonYears() <= 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "horizonYears must be > 0");
    }
  }

  private WealthPlan toDto(WealthPlanEntity entity) {
    WealthPlan plan = new WealthPlan();
    plan.setId(entity.getId());
    plan.setUserId(entity.getUserId());
    plan.setPlanType(WealthPlanType.valueOf(entity.getPlanType()));
    plan.setName(entity.getName());
    plan.setStartingBalance(entity.getStartingBalance());
    plan.setTargetBalance(entity.getTargetBalance());
    plan.setMonthlyContribution(entity.getMonthlyContribution());
    plan.setHorizonYears(entity.getHorizonYears());
    plan.setExpectedReturn(entity.getExpectedReturn());
    plan.setVolatility(entity.getVolatility());
    plan.setSimulationCount(entity.getSimulationCount());
    plan.setSuccessProbability(entity.getSuccessProbability());
    plan.setMedianOutcome(entity.getMedianOutcome());
    plan.setP10Outcome(entity.getP10Outcome());
    plan.setP90Outcome(entity.getP90Outcome());
    plan.setCreatedAt(entity.getCreatedAt());
    plan.setUpdatedAt(entity.getUpdatedAt());
    plan.setLastSimulatedAt(entity.getLastSimulatedAt());
    return plan;
  }

  private WealthPlanEntity toEntity(WealthPlan plan) {
    WealthPlanEntity entity = new WealthPlanEntity();
    entity.setId(plan.getId());
    entity.setUserId(plan.getUserId());
    entity.setOrgId(tenantContext.getOrgId());
    entity.setPlanType(plan.getPlanType().name());
    entity.setName(plan.getName());
    entity.setStartingBalance(plan.getStartingBalance());
    entity.setTargetBalance(plan.getTargetBalance());
    entity.setMonthlyContribution(plan.getMonthlyContribution());
    entity.setHorizonYears(plan.getHorizonYears());
    entity.setExpectedReturn(plan.getExpectedReturn());
    entity.setVolatility(plan.getVolatility());
    entity.setSimulationCount(plan.getSimulationCount());
    entity.setSuccessProbability(plan.getSuccessProbability());
    entity.setMedianOutcome(plan.getMedianOutcome());
    entity.setP10Outcome(plan.getP10Outcome());
    entity.setP90Outcome(plan.getP90Outcome());
    entity.setCreatedAt(plan.getCreatedAt());
    entity.setUpdatedAt(plan.getUpdatedAt());
    entity.setLastSimulatedAt(plan.getLastSimulatedAt());
    return entity;
  }
}
