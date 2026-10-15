package com.alphamath.portfolio.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WatchlistRepository extends JpaRepository<WatchlistEntity, String> {
  List<WatchlistEntity> findByUserIdOrderByUpdatedAtDesc(String userId);
  WatchlistEntity findByIdAndUserId(String id, String userId);
  List<WatchlistEntity> findByUserIdAndOrgIdOrderByUpdatedAtDesc(String userId, String orgId);
  WatchlistEntity findByIdAndUserIdAndOrgId(String id, String userId, String orgId);

  long countByOrgId(String orgId);
}
