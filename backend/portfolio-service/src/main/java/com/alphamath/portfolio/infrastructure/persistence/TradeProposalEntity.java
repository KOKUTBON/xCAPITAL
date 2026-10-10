package com.alphamath.portfolio.infrastructure.persistence;

import com.alphamath.portfolio.domain.execution.AssetClass;
import com.alphamath.portfolio.domain.execution.OrderType;
import com.alphamath.portfolio.domain.execution.Region;
import com.alphamath.portfolio.domain.execution.TimeInForce;
import com.alphamath.portfolio.domain.trade.ExecutionMode;
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
@Table(name = "portfolio_trade_proposal")
@Data
public class TradeProposalEntity {
  @Id
  private String id;

  @Column(nullable = false)
  private String userId;
  @Column(name = "org_id")
  private String orgId;


  @Column(name = "account_id")
  private String accountId;

  @Column(nullable = false)
  private String status;

  private String approvedBy;
  private Instant approvedAt;
  private String approvalHash;

  private Instant createdAt;
  private Instant updatedAt;

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

  private Double expectedReturn;
  private Double variance;
  private Double totalEquity;
  private Double turnover;
  private Double scaledBuyFactor;
  private Double feeBps;
  private Double feeTotal;

  @Column(name = "ai_summary")
  private String aiSummary;

  @Column(name = "ai_confidence")
  private Double aiConfidence;

  @Column(name = "ai_expected_return")
  private Double aiExpectedReturn;

  @Column(name = "ai_volatility")
  private Double aiVolatility;

  @Column(name = "ai_p_up")
  private Double aiPUp;

  @Column(name = "ai_horizon")
  private Integer aiHorizon;

  @Column(name = "ai_model")
  private String aiModel;

  @Column(name = "ai_disclaimer")
  private String aiDisclaimer;

  private String disclaimer;

  @Column(name = "execution_intent_id")
  private String executionIntentId;

  @Column(name = "execution_json")
  private String executionJson;

  @Column(name = "symbols_json")
  private String symbolsJson;

  @Column(name = "prices_json")
  private String pricesJson;

  @Column(name = "current_weights_json")
  private String currentWeightsJson;

  @Column(name = "target_weights_json")
  private String targetWeightsJson;

  @Column(name = "policy_checks_json")
  private String policyChecksJson;

  @Column(nullable = false)
  private String payload;
}
