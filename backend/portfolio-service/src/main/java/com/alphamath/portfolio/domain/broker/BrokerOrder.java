package com.alphamath.portfolio.domain.broker;

import com.alphamath.portfolio.domain.execution.OrderType;
import com.alphamath.portfolio.domain.execution.TimeInForce;
import com.alphamath.portfolio.domain.trade.TradeSide;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class BrokerOrder {
  private String id;
  @JsonIgnore
  private String userId;
  private String brokerAccountId;
  private String externalOrderId;
  private String clientOrderId;
  private BrokerOrderStatus status;
  private OrderType orderType;
  private TradeSide side;
  private TimeInForce timeInForce;
  private Instant submittedAt;
  private Instant updatedAt;
  private Instant filledAt;
  private Double totalQuantity;
  private Double filledQuantity;
  private Double avgPrice;
  private String currency;
  private Map<String, Object> metadata = new LinkedHashMap<>();
  private List<BrokerOrderLeg> legs = new ArrayList<>();
}
