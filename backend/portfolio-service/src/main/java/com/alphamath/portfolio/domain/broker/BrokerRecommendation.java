package com.alphamath.portfolio.domain.broker;

import com.alphamath.portfolio.domain.execution.AssetClass;
import com.alphamath.portfolio.domain.execution.OrderType;
import com.alphamath.portfolio.domain.execution.Region;
import com.alphamath.portfolio.domain.execution.TimeInForce;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class BrokerRecommendation {
  private String brokerId;
  private String brokerName;
  private String accountId;
  private Region region;
  private BrokerIntegrationStatus integrationStatus;
  private List<AssetClass> assetClasses = new ArrayList<>();
  private List<OrderType> orderTypes = new ArrayList<>();
  private List<TimeInForce> timeInForce = new ArrayList<>();
  private List<String> reasons = new ArrayList<>();
  private int score;
}
