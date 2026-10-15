package com.alphamath.portfolio.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreferenceEntity, String> {
  List<NotificationPreferenceEntity> findByUserIdOrderByChannelAsc(String userId);

  NotificationPreferenceEntity findByUserIdAndChannel(String userId, String channel);

  List<NotificationPreferenceEntity> findByUserIdAndOrgIdOrderByChannelAsc(String userId, String orgId);

  NotificationPreferenceEntity findByUserIdAndOrgIdAndChannel(String userId, String orgId, String channel);
}
