package com.alphamath.portfolio.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BrokerConnectionRepository extends JpaRepository<BrokerConnectionEntity, String> {
  List<BrokerConnectionEntity> findByUserIdOrderByCreatedAtDesc(String userId);
  List<BrokerConnectionEntity> findByUserIdAndOrgIdOrderByCreatedAtDesc(String userId, String orgId);
  BrokerConnectionEntity findByIdAndUserId(String id, String userId);
  BrokerConnectionEntity findByIdAndUserIdAndOrgId(String id, String userId, String orgId);
}
