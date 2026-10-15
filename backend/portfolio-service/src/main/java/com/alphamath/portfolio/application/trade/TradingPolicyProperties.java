package com.alphamath.portfolio.application.trade;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "alphamath.trading.policy")
public class TradingPolicyProperties {
  private double maxSingleOrderPctEquity = 0.25;
  private double maxSinglePositionPctEquity = 0.4;
  private double minOrderNotional = 10.0;
  private double maxSingleOrderNotional = 250000.0;
  private double maxGrossNotional = 500000.0;
  private double maxNetNotional = 250000.0;
  private double priceDeviationWarnPct = 0.1;
  private double riskVolatilityWarn = 0.08;
  private double riskDrawdownWarn = 0.2;

  public double getMaxSingleOrderPctEquity() {
    return maxSingleOrderPctEquity;
  }

  public void setMaxSingleOrderPctEquity(double maxSingleOrderPctEquity) {
    this.maxSingleOrderPctEquity = maxSingleOrderPctEquity;
  }

  public double getMaxSinglePositionPctEquity() {
    return maxSinglePositionPctEquity;
  }

  public void setMaxSinglePositionPctEquity(double maxSinglePositionPctEquity) {
    this.maxSinglePositionPctEquity = maxSinglePositionPctEquity;
  }

  public double getMinOrderNotional() {
    return minOrderNotional;
  }

  public void setMinOrderNotional(double minOrderNotional) {
    this.minOrderNotional = minOrderNotional;
  }

  public double getMaxSingleOrderNotional() {
    return maxSingleOrderNotional;
  }

  public void setMaxSingleOrderNotional(double maxSingleOrderNotional) {
    this.maxSingleOrderNotional = maxSingleOrderNotional;
  }

  public double getMaxGrossNotional() {
    return maxGrossNotional;
  }

  public void setMaxGrossNotional(double maxGrossNotional) {
    this.maxGrossNotional = maxGrossNotional;
  }

  public double getMaxNetNotional() {
    return maxNetNotional;
  }

  public void setMaxNetNotional(double maxNetNotional) {
    this.maxNetNotional = maxNetNotional;
  }

  public double getPriceDeviationWarnPct() {
    return priceDeviationWarnPct;
  }

  public void setPriceDeviationWarnPct(double priceDeviationWarnPct) {
    this.priceDeviationWarnPct = priceDeviationWarnPct;
  }

  public double getRiskVolatilityWarn() {
    return riskVolatilityWarn;
  }

  public void setRiskVolatilityWarn(double riskVolatilityWarn) {
    this.riskVolatilityWarn = riskVolatilityWarn;
  }

  public double getRiskDrawdownWarn() {
    return riskDrawdownWarn;
  }

  public void setRiskDrawdownWarn(double riskDrawdownWarn) {
    this.riskDrawdownWarn = riskDrawdownWarn;
  }
}
