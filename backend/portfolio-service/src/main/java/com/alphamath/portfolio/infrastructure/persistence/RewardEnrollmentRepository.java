package com.alphamath.portfolio.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RewardEnrollmentRepository extends JpaRepository<RewardEnrollmentEntity, String> {
  List<RewardEnrollmentEntity> findByUserIdOrderByCreatedAtDesc(String userId);
  List<RewardEnrollmentEntity> findByUserIdAndOrgIdOrderByCreatedAtDesc(String userId, String orgId);
  List<RewardEnrollmentEntity> findByStatusOrderByCreatedAtDesc(String status);
  List<RewardEnrollmentEntity> findByOrgIdAndStatusOrderByCreatedAtDesc(String orgId, String status);

  long countByOrgId(String orgId);
}
