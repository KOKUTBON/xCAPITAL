package com.alphamath.portfolio.application.broker;

import com.alphamath.portfolio.domain.broker.BrokerConnection;
import com.alphamath.portfolio.domain.broker.BrokerOrder;
import com.alphamath.portfolio.domain.broker.BrokerOrderPreview;
import com.alphamath.portfolio.domain.broker.BrokerOrderRequest;
import com.alphamath.portfolio.domain.broker.BrokerOrderStatus;

import java.time.Instant;
import java.util.LinkedHashMap;

public interface BrokerAdapter {
  boolean supports(String brokerId);
  BrokerSnapshot sync(BrokerConnection connection);
  BrokerOrder placeOrder(BrokerConnection connection, BrokerOrderRequest request);

  default BrokerOrderPreview previewOrder(BrokerConnection connection, BrokerOrderRequest request) {
    BrokerOrderPreview preview = new BrokerOrderPreview();
    preview.setSymbol(request.getSymbol());
    preview.setAssetClass(request.getAssetClass());
    preview.setSide(request.getSide());
    preview.setQuantity(request.getQuantity());
    preview.setOrderType(request.getOrderType());
    preview.setTimeInForce(request.getTimeInForce());
    preview.setPrice(request.getLimitPrice());
    preview.setCurrency(request.getCurrency());
    preview.setEstimatedNotional(null);
    preview.setEstimatedFees(null);
    preview.setEstimatedTotal(null);
    preview.setMetadata(new LinkedHashMap<>());
    preview.getWarnings().add("Order preview not supported for broker");
    preview.setCreatedAt(Instant.now());
    return preview;
  }

  default BrokerOrder refreshOrder(BrokerConnection connection, BrokerOrder order) {
    return order;
  }

  default BrokerOrder cancelOrder(BrokerConnection connection, BrokerOrder order) {
    order.setStatus(BrokerOrderStatus.CANCELED);
    order.setUpdatedAt(Instant.now());
    return order;
  }
}
