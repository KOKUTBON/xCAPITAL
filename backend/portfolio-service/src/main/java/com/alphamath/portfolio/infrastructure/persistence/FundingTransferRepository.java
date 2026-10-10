package com.alphamath.portfolio.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FundingTransferRepository extends JpaRepository<FundingTransferEntity, String> {
  List<FundingTransferEntity> findByUserIdOrderByCreatedAtDesc(String userId);
  List<FundingTransferEntity> findByUserIdAndOrgIdOrderByCreatedAtDesc(String userId, String orgId);
}
