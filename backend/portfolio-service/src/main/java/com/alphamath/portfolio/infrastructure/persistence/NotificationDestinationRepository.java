package com.alphamath.portfolio.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationDestinationRepository extends JpaRepository<NotificationDestinationEntity, String> {
  List<NotificationDestinationEntity> findByUserIdOrderByCreatedAtDesc(String userId);

  List<NotificationDestinationEntity> findByUserIdAndChannelOrderByCreatedAtDesc(String userId, String channel);

  List<NotificationDestinationEntity> findByUserIdAndChannelAndStatus(String userId, String channel, String status);

  List<NotificationDestinationEntity> findByUserIdAndOrgIdOrderByCreatedAtDesc(String userId, String orgId);

  List<NotificationDestinationEntity> findByUserIdAndOrgIdAndChannelOrderByCreatedAtDesc(String userId, String orgId, String channel);

  List<NotificationDestinationEntity> findByUserIdAndOrgIdAndChannelAndStatus(String userId, String orgId, String channel, String status);
}
