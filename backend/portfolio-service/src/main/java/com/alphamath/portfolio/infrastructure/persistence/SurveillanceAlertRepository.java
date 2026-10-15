package com.alphamath.portfolio.infrastructure.persistence;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SurveillanceAlertRepository extends JpaRepository<SurveillanceAlertEntity, String> {
  List<SurveillanceAlertEntity> findByUserIdOrderByCreatedAtDesc(String userId, PageRequest page);
  List<SurveillanceAlertEntity> findByUserIdAndOrgIdOrderByCreatedAtDesc(String userId, String orgId, PageRequest page);

  long countByOrgId(String orgId);
}
