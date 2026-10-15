package com.alphamath.portfolio.domain.trade;

import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class TradeExecution {
  private Instant executedAt;
  private List<TradeOrder> filledOrders = new ArrayList<>();
  private double cashAfter;
  private Map<String, Double> positionsAfter = new LinkedHashMap<>();
  private double feeTotal;
  private String note;
}
