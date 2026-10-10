package com.alphamath.portfolio.domain.autoinvest;

import com.alphamath.portfolio.domain.execution.AssetClass;
import com.alphamath.portfolio.domain.execution.OrderType;
import com.alphamath.portfolio.domain.execution.Region;
import com.alphamath.portfolio.domain.execution.TimeInForce;
import com.alphamath.portfolio.domain.trade.ExecutionMode;
import com.alphamath.portfolio.math.Optimizers;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AutoInvestPlanRequest {
  @NotNull
  @Size(min = 3, max = 64)
  private String name;

  private AutoInvestGoalType goalType = AutoInvestGoalType.GENERAL_INVESTING;

  @NotNull @Size(min = 2, max = 50)
  private List<@NotNull String> symbols = new ArrayList<>();

  @NotNull
  private AutoInvestSchedule schedule = AutoInvestSchedule.DAILY;

  private String scheduleTimeUtc = "09:30";
  private String scheduleDayOfWeek = "MONDAY";

  private Double driftThreshold = 0.05;
  private Integer returnsLookback = 90;
  private boolean useMarketData = true;
  private boolean useAiForecast = false;

  @Min(1) @Max(30)
  private Integer aiHorizon = 1;

  private Optimizers.Method method = Optimizers.Method.MEAN_VARIANCE_PGD;

  @NotNull @Min(1) @Max(100)
  private Integer riskAversion = 6;

  @NotNull
  private Double maxWeight = 0.6;

  @NotNull
  private Double minWeight = 0.0;

  private Double minTradeValue = 10.0;
  private Double maxTradePctOfEquity = 0.25;
  private Double maxTurnover = 0.7;
  private Double advisoryFeeBpsAnnual;
  private Double minimumBalance;

  private ExecutionMode executionMode = ExecutionMode.PAPER;
  private Region region = Region.US;
  private AssetClass assetClass = AssetClass.EQUITY;
  private String providerPreference;
  private OrderType orderType = OrderType.MARKET;
  private TimeInForce timeInForce = TimeInForce.DAY;

  private List<@NotNull Double> mu = new ArrayList<>();
  private List<List<@NotNull Double>> cov = new ArrayList<>();
}
