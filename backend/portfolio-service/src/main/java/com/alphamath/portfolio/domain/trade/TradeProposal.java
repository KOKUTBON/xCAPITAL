package com.alphamath.portfolio.domain.trade;

import com.alphamath.portfolio.domain.execution.AssetClass;
import com.alphamath.portfolio.domain.execution.OrderType;
import com.alphamath.portfolio.domain.execution.Region;
import com.alphamath.portfolio.domain.execution.TimeInForce;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class TradeProposal {
  private String id;
  @JsonIgnore
  private String userId;
  private Instant createdAt;
  private TradeStatus status;

  @JsonIgnore
  private String approvedBy;
  @JsonIgnore
  private Instant approvedAt;
  @JsonIgnore
  private String approvalHash;

  private ExecutionMode executionMode = ExecutionMode.PAPER;
  private Region region = Region.US;
  private AssetClass assetClass = AssetClass.EQUITY;
  private String providerPreference;
  private OrderType orderType = OrderType.MARKET;
  private TimeInForce timeInForce = TimeInForce.DAY;

  private List<String> symbols = new ArrayList<>();
  private Map<String, Double> prices = new LinkedHashMap<>();
  private Map<String, Double> currentWeights = new LinkedHashMap<>();
  private Map<String, Double> targetWeights = new LinkedHashMap<>();

  private double expectedReturn;
  private double variance;
  private double totalEquity;
  private double turnover;
  private double scaledBuyFactor;
  private double feeBps;
  private double feeTotal;

  private List<TradeOrder> orders = new ArrayList<>();
  private List<PolicyCheck> policyChecks = new ArrayList<>();
  private AiRecommendation ai;

  private String disclaimer;
  private TradeExecution execution;
  private String executionIntentId;
}
