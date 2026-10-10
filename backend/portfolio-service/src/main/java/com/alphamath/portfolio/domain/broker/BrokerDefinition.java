package com.alphamath.portfolio.domain.broker;

import com.alphamath.portfolio.domain.execution.AssetClass;
import com.alphamath.portfolio.domain.execution.OrderType;
import com.alphamath.portfolio.domain.execution.Region;
import com.alphamath.portfolio.domain.execution.TimeInForce;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class BrokerDefinition {
  private String id;
  private String name;
  private List<Region> regions = new ArrayList<>();
  private List<AssetClass> assetClasses = new ArrayList<>();
  private List<OrderType> orderTypes = new ArrayList<>();
  private List<TimeInForce> timeInForce = new ArrayList<>();
  private List<String> features = new ArrayList<>();
  private int score;
  private BrokerIntegrationStatus integrationStatus = BrokerIntegrationStatus.PENDING_KEYS;
  private String notes;
}
