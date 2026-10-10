package com.alphamath.portfolio.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AutoInvestPlanRepository extends JpaRepository<AutoInvestPlanEntity, String> {
  List<AutoInvestPlanEntity> findByUserIdOrderByCreatedAtDesc(String userId);
  List<AutoInvestPlanEntity> findByUserIdAndOrgIdOrderByCreatedAtDesc(String userId, String orgId);
  List<AutoInvestPlanEntity> findByStatusOrderByCreatedAtAsc(String status);
  List<AutoInvestPlanEntity> findByOrgIdAndStatusOrderByCreatedAtAsc(String orgId, String status);

  long countByOrgId(String orgId);
}
