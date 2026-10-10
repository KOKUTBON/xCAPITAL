package com.alphamath.portfolio.domain.broker;

import com.alphamath.portfolio.domain.execution.AssetClass;
import com.alphamath.portfolio.domain.trade.TradeSide;
import lombok.Data;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class BrokerOrderLeg {
  private String id;
  private String orderId;
  private String instrumentId;
  private String symbol;
  private AssetClass assetClass;
  private TradeSide side;
  private double quantity;
  private Double limitPrice;
  private Double stopPrice;
  private OptionType optionType;
  private Double strike;
  private LocalDate expiry;
  private Map<String, Object> metadata = new LinkedHashMap<>();
}
