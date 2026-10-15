package com.alphamath.portfolio.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BrokerAccountRepository extends JpaRepository<BrokerAccountEntity, String> {
  List<BrokerAccountEntity> findByUserIdOrderByCreatedAtDesc(String userId);
  List<BrokerAccountEntity> findByUserIdAndOrgIdOrderByCreatedAtDesc(String userId, String orgId);
  BrokerAccountEntity findByUserIdAndExternalAccountId(String userId, String externalAccountId);
  BrokerAccountEntity findByUserIdAndOrgIdAndExternalAccountId(String userId, String orgId, String externalAccountId);
  List<BrokerAccountEntity> findByBrokerConnectionIdOrderByCreatedAtDesc(String brokerConnectionId);

  long countByOrgId(String orgId);
}
