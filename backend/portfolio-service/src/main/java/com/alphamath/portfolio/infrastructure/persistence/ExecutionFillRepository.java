package com.alphamath.portfolio.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExecutionFillRepository extends JpaRepository<ExecutionFillEntity, String> {
  List<ExecutionFillEntity> findByIntentIdOrderByFilledAtAsc(String intentId);
}
