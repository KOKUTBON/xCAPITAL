package com.alphamath.portfolio.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FundingWithdrawalRepository extends JpaRepository<FundingWithdrawalEntity, String> {
  List<FundingWithdrawalEntity> findByUserIdOrderByCreatedAtDesc(String userId);
  List<FundingWithdrawalEntity> findByUserIdAndOrgIdOrderByCreatedAtDesc(String userId, String orgId);
}
