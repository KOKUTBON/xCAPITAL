package com.alphamath.portfolio.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AutoInvestRunRepository extends JpaRepository<AutoInvestRunEntity, String> {
  List<AutoInvestRunEntity> findByPlanIdOrderByCreatedAtDesc(String planId);
  Optional<AutoInvestRunEntity> findByIdempotencyKey(String idempotencyKey);
}
