package com.alphamath.portfolio.infrastructure.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<NotificationEntity, String> {
  List<NotificationEntity> findByUserIdOrderByCreatedAtDesc(String userId, Pageable page);
  List<NotificationEntity> findByUserIdAndStatusOrderByCreatedAtDesc(String userId, String status, Pageable page);
  List<NotificationEntity> findByUserIdAndOrgIdOrderByCreatedAtDesc(String userId, String orgId, Pageable page);
  List<NotificationEntity> findByUserIdAndOrgIdAndStatusOrderByCreatedAtDesc(String userId, String orgId, String status, Pageable page);

  long countByOrgId(String orgId);
}
