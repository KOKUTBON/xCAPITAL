package com.alphamath.portfolio.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WealthPlanRepository extends JpaRepository<WealthPlanEntity, String> {
  List<WealthPlanEntity> findByUserIdOrderByCreatedAtDesc(String userId);
  List<WealthPlanEntity> findByUserIdAndOrgIdOrderByCreatedAtDesc(String userId, String orgId);

  long countByOrgId(String orgId);
}
