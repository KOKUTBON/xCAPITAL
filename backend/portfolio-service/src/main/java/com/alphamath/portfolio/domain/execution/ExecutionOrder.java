package com.alphamath.portfolio.domain.execution;

import com.alphamath.portfolio.domain.trade.TradeSide;
import lombok.Data;

@Data
public class ExecutionOrder {
  private String symbol;
  private TradeSide side;
  private double quantity;
  private Double price;
  private OrderType orderType;
  private TimeInForce timeInForce;
  private Double limitPrice;
}
