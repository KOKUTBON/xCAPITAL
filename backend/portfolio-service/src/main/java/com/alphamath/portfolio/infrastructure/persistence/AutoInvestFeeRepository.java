package com.alphamath.portfolio.infrastructure.persistence;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AutoInvestFeeRepository extends JpaRepository<AutoInvestFeeEntity, String> {
  List<AutoInvestFeeEntity> findByPlanIdOrderByCreatedAtDesc(String planId, PageRequest page);
}
