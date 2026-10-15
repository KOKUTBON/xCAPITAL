package com.alphamath.portfolio.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FundingDepositRepository extends JpaRepository<FundingDepositEntity, String> {
  List<FundingDepositEntity> findByUserIdOrderByCreatedAtDesc(String userId);
  List<FundingDepositEntity> findByUserIdAndOrgIdOrderByCreatedAtDesc(String userId, String orgId);
}
