package com.alphamath.portfolio.infrastructure.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDeliveryEntity, String> {
  List<NotificationDeliveryEntity> findByUserIdOrderByCreatedAtDesc(String userId, Pageable page);

  List<NotificationDeliveryEntity> findByUserIdAndStatusOrderByCreatedAtDesc(String userId, String status, Pageable page);

  List<NotificationDeliveryEntity> findByUserIdAndOrgIdOrderByCreatedAtDesc(String userId, String orgId, Pageable page);

  List<NotificationDeliveryEntity> findByUserIdAndOrgIdAndStatusOrderByCreatedAtDesc(String userId, String orgId, String status, Pageable page);

  List<NotificationDeliveryEntity> findByStatusOrderByCreatedAtAsc(String status, Pageable page);

  List<NotificationDeliveryEntity> findByStatusInOrderByCreatedAtAsc(List<String> statuses, Pageable page);
}
