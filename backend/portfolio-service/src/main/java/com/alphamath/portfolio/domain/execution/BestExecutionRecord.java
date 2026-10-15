package com.alphamath.portfolio.domain.execution;

import com.alphamath.portfolio.domain.trade.TradeSide;
import lombok.Data;

import java.time.Instant;

@Data
public class BestExecutionRecord {
  private String id;
  private String userId;
  private String proposalId;
  private String symbol;
  private TradeSide side;
  private Double requestedPrice;
  private Double executedPrice;
  private Double marketPrice;
  private Double slippageBps;
  private Instant createdAt;
}
