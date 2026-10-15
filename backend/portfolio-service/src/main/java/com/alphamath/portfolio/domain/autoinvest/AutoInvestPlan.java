package com.alphamath.portfolio.domain.autoinvest;

import com.alphamath.portfolio.domain.execution.AssetClass;
import com.alphamath.portfolio.domain.execution.OrderType;
import com.alphamath.portfolio.domain.execution.Region;
import com.alphamath.portfolio.domain.execution.TimeInForce;
import com.alphamath.portfolio.domain.trade.ExecutionMode;
import com.alphamath.portfolio.math.Optimizers;
import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
public class AutoInvestPlan {
  private String id;
  private String userId;
  private String orgId;
  private String name;
  private AutoInvestPlanStatus status;
  private AutoInvestGoalType goalType;
  private AutoInvestSchedule schedule;
  private String scheduleTimeUtc;
  private String scheduleDayOfWeek;
  private Double driftThreshold;
  private Integer returnsLookback;
  private boolean useMarketData;
  private boolean useAiForecast;
  private Integer aiHorizon;
  private Optimizers.Method method;
  private Integer riskAversion;
  private Double maxWeight;
  private Double minWeight;
  private Double minTradeValue;
  private Double maxTradePctOfEquity;
  private Double maxTurnover;
  private Double advisoryFeeBpsAnnual;
  private Double minimumBalance;
  private ExecutionMode executionMode;
  private Region region;
  private AssetClass assetClass;
  private String providerPreference;
  private OrderType orderType;
  private TimeInForce timeInForce;
  private List<String> symbols = new ArrayList<>();
  private List<Double> mu = new ArrayList<>();
  private List<List<Double>> cov = new ArrayList<>();
  private Instant createdAt;
  private Instant updatedAt;
  private Instant lastRunAt;
  private Instant lastFeeChargedAt;
}
