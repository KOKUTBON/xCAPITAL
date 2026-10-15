package com.alphamath.portfolio.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FxRateRepository extends JpaRepository<FxRateEntity, String> {
  List<FxRateEntity> findByBaseCcyAndQuoteCcyOrderByTsDesc(String baseCcy, String quoteCcy);
}
