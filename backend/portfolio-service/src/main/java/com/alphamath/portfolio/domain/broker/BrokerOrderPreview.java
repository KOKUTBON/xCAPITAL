package com.alphamath.portfolio.domain.broker;

import com.alphamath.portfolio.domain.execution.AssetClass;
import com.alphamath.portfolio.domain.execution.OrderType;
import com.alphamath.portfolio.domain.execution.TimeInForce;
import com.alphamath.portfolio.domain.trade.TradeSide;
import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class BrokerOrderPreview {
  private String brokerAccountId;
  private String brokerId;
  private String symbol;
  private AssetClass assetClass;
  private TradeSide side;
  private double quantity;
  private OrderType orderType;
  private TimeInForce timeInForce;
  private Double price;
  private Double estimatedNotional;
  private Double estimatedFees;
  private Double estimatedTotal;
  private String currency;
  private List<String> warnings = new ArrayList<>();
  private Map<String, Object> metadata = new LinkedHashMap<>();
  private Instant createdAt;
}
