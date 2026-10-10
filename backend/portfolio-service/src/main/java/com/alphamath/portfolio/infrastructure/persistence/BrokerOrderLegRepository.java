package com.alphamath.portfolio.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BrokerOrderLegRepository extends JpaRepository<BrokerOrderLegEntity, String> {
  List<BrokerOrderLegEntity> findByOrderId(String orderId);
  void deleteByOrderId(String orderId);
}
