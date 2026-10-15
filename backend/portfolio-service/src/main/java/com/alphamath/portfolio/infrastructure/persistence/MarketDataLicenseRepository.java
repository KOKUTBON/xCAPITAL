package com.alphamath.portfolio.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MarketDataLicenseRepository extends JpaRepository<MarketDataLicenseEntity, String> {
  List<MarketDataLicenseEntity> findByUserIdOrderByCreatedAtDesc(String userId);

  List<MarketDataLicenseEntity> findByUserIdAndStatusOrderByCreatedAtDesc(String userId, String status);

  List<MarketDataLicenseEntity> findByUserIdAndOrgIdOrderByCreatedAtDesc(String userId, String orgId);

  List<MarketDataLicenseEntity> findByUserIdAndOrgIdAndStatusOrderByCreatedAtDesc(String userId, String orgId, String status);

  long countByOrgId(String orgId);
}
