package com.alphamath.portfolio.infrastructure.persistence;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BestExecutionRepository extends JpaRepository<BestExecutionEntity, String> {
  List<BestExecutionEntity> findByUserIdOrderByCreatedAtDesc(String userId, PageRequest page);
  List<BestExecutionEntity> findByUserIdAndSymbolOrderByCreatedAtDesc(String userId, String symbol, PageRequest page);
  List<BestExecutionEntity> findByUserIdAndOrgIdOrderByCreatedAtDesc(String userId, String orgId, PageRequest page);
  List<BestExecutionEntity> findByUserIdAndOrgIdAndSymbolOrderByCreatedAtDesc(String userId, String orgId, String symbol, PageRequest page);
}
