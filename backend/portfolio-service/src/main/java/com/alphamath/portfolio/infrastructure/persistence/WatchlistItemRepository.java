package com.alphamath.portfolio.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WatchlistItemRepository extends JpaRepository<WatchlistItemEntity, String> {
  List<WatchlistItemEntity> findByWatchlistIdOrderByCreatedAtDesc(String watchlistId);
  void deleteByWatchlistId(String watchlistId);
}
