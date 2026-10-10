package com.alphamath.portfolio.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BrokerOrderRepository extends JpaRepository<BrokerOrderEntity, String> {
  List<BrokerOrderEntity> findByUserIdAndBrokerAccountIdOrderByUpdatedAtDesc(String userId, String brokerAccountId);
  List<BrokerOrderEntity> findByUserIdAndOrgIdAndBrokerAccountIdOrderByUpdatedAtDesc(String userId, String orgId, String brokerAccountId);
  BrokerOrderEntity findByUserIdAndExternalOrderId(String userId, String externalOrderId);
  BrokerOrderEntity findByUserIdAndOrgIdAndExternalOrderId(String userId, String orgId, String externalOrderId);
  BrokerOrderEntity findByUserIdAndBrokerAccountIdAndClientOrderId(String userId,
                                                                   String brokerAccountId,
                                                                   String clientOrderId);
  BrokerOrderEntity findByUserIdAndOrgIdAndBrokerAccountIdAndClientOrderId(String userId,
                                                                           String orgId,
                                                                           String brokerAccountId,
                                                                           String clientOrderId);
  BrokerOrderEntity findByIdAndUserId(String id, String userId);
  BrokerOrderEntity findByIdAndUserIdAndOrgId(String id, String userId, String orgId);
  void deleteByUserIdAndBrokerAccountId(String userId, String brokerAccountId);
  void deleteByUserIdAndOrgIdAndBrokerAccountId(String userId, String orgId, String brokerAccountId);
}
