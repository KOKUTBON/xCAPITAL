package com.alphamath.portfolio.infrastructure.persistence;

import com.alphamath.portfolio.domain.broker.OptionType;
import com.alphamath.portfolio.domain.execution.AssetClass;
import com.alphamath.portfolio.domain.trade.TradeSide;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDate;

@Entity
@Table(name = "portfolio_broker_order_leg")
@Data
public class BrokerOrderLegEntity {
  @Id
  private String id;

  @Column(nullable = false)
  private String orderId;

  private String instrumentId;

  @Column(nullable = false)
  private String symbol;

  @Enumerated(EnumType.STRING)
  private AssetClass assetClass;

  @Enumerated(EnumType.STRING)
  private TradeSide side;

  @Column(nullable = false)
  private double quantity;

  private Double limitPrice;

  private Double stopPrice;

  @Enumerated(EnumType.STRING)
  private OptionType optionType;

  private Double strike;

  private LocalDate expiry;

  @Lob
  private String metadataJson;
}
