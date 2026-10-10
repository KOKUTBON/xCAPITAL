package com.alphamath.portfolio.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RewardOfferRepository extends JpaRepository<RewardOfferEntity, String> {
  List<RewardOfferEntity> findByStatusOrderByCreatedAtDesc(String status);
}
