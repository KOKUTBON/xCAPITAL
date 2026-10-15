package com.alphamath.portfolio.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FundingSourceRepository extends JpaRepository<FundingSourceEntity, String> {
  List<FundingSourceEntity> findByUserIdOrderByCreatedAtDesc(String userId);
  List<FundingSourceEntity> findByUserIdAndOrgIdOrderByCreatedAtDesc(String userId, String orgId);

  long countByOrgId(String orgId);
}
