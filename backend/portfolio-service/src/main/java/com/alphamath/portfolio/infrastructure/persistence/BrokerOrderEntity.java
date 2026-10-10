package com.alphamath.portfolio.infrastructure.persistence;

import com.alphamath.portfolio.domain.broker.BrokerOrderStatus;
import com.alphamath.portfolio.domain.execution.OrderType;
import com.alphamath.portfolio.domain.execution.TimeInForce;
import com.alphamath.portfolio.domain.trade.TradeSide;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "portfolio_broker_order")
@Data
public class BrokerOrderEntity {
  @Id
  private String id;

  @Column(nullable = false)
  private String userId;
  @Column(name = "org_id")
  private String orgId;


  @Column(nullable = false)
  private String brokerAccountId;

  private String externalOrderId;

  @Column(name = "client_order_id")
  private String clientOrderId;

  @Enumerated(EnumType.STRING)
  private BrokerOrderStatus status;

  @Enumerated(EnumType.STRING)
  private OrderType orderType;

  @Enumerated(EnumType.STRING)
  private TradeSide side;

  @Enumerated(EnumType.STRING)
  private TimeInForce timeInForce;

  private Instant submittedAt;

  private Instant updatedAt;

  private Instant filledAt;

  private Double totalQuantity;

  private Double filledQuantity;

  private Double avgPrice;

  private String currency;

  @Lob
  private String metadataJson;
}
