package com.alphamath.portfolio.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MarketDataEntitlementRepository extends JpaRepository<MarketDataEntitlementEntity, String> {
  List<MarketDataEntitlementEntity> findByUserIdOrderByCreatedAtDesc(String userId);

  List<MarketDataEntitlementEntity> findByUserIdAndStatusOrderByCreatedAtDesc(String userId, String status);

  List<MarketDataEntitlementEntity> findByUserIdAndEntitlementTypeAndStatus(String userId, String entitlementType, String status);

  List<MarketDataEntitlementEntity> findByUserIdAndOrgIdOrderByCreatedAtDesc(String userId, String orgId);

  List<MarketDataEntitlementEntity> findByUserIdAndOrgIdAndStatusOrderByCreatedAtDesc(String userId, String orgId, String status);

  List<MarketDataEntitlementEntity> findByUserIdAndOrgIdAndEntitlementTypeAndStatus(String userId, String orgId, String entitlementType,
                                                                                   String status);

  long countByOrgId(String orgId);
}
