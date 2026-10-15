package com.alphamath.portfolio.infrastructure.persistence;

import com.alphamath.portfolio.domain.execution.AssetClass;
import com.alphamath.portfolio.domain.execution.OrderType;
import com.alphamath.portfolio.domain.execution.Region;
import com.alphamath.portfolio.domain.execution.TimeInForce;
import com.alphamath.portfolio.domain.trade.ExecutionMode;
import com.alphamath.portfolio.math.Optimizers;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "portfolio_auto_invest_plan")
@Data
public class AutoInvestPlanEntity {
  @Id
  private String id;

  @Column(nullable = false)
  private String userId;

  @Column(name = "org_id")
  private String orgId;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private String status;

  @Column(nullable = false)
  private String schedule;

  private String goalType;

  private String scheduleTimeUtc;
  private String scheduleDayOfWeek;
  private Double driftThreshold;
  private Integer returnsLookback;
  private boolean useMarketData;
  private boolean useAiForecast;
  private Integer aiHorizon;

  @Enumerated(EnumType.STRING)
  private Optimizers.Method method;

  @Column(nullable = false)
  private Integer riskAversion;

  @Column(nullable = false)
  private Double maxWeight;

  @Column(nullable = false)
  private Double minWeight;

  private Double minTradeValue;
  private Double maxTradePct;
  private Double maxTurnover;

  private Double advisoryFeeBpsAnnual;
  private Double minimumBalance;

  @Enumerated(EnumType.STRING)
  private ExecutionMode executionMode;

  @Enumerated(EnumType.STRING)
  private Region region;

  @Enumerated(EnumType.STRING)
  private AssetClass assetClass;

  private String providerPreference;

  @Enumerated(EnumType.STRING)
  private OrderType orderType;

  @Enumerated(EnumType.STRING)
  private TimeInForce timeInForce;

  @Lob
  @Column(nullable = false)
  private String symbolsJson;

  @Lob
  private String muJson;

  @Lob
  private String covJson;

  private Instant createdAt;
  private Instant updatedAt;
  private Instant lastRunAt;
  private Instant lastFeeChargedAt;
}
