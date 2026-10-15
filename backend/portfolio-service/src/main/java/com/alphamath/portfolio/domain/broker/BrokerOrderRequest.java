package com.alphamath.portfolio.domain.broker;

import com.alphamath.portfolio.domain.execution.AssetClass;
import com.alphamath.portfolio.domain.execution.OrderType;
import com.alphamath.portfolio.domain.execution.TimeInForce;
import com.alphamath.portfolio.domain.trade.TradeSide;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class BrokerOrderRequest {
  private String clientOrderId;
  private String instrumentId;
  private String symbol;
  private AssetClass assetClass;
  private TradeSide side;
  private double quantity;
  private OrderType orderType = OrderType.MARKET;
  private TimeInForce timeInForce = TimeInForce.DAY;
  private Double limitPrice;
  private Double stopPrice;
  private String currency;
  private boolean allowFractional = true;
  private List<BrokerOrderLegRequest> legs = new ArrayList<>();
  private Map<String, Object> metadata = new LinkedHashMap<>();
}
