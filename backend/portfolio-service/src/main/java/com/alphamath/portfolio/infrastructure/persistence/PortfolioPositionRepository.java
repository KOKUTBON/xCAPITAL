package com.alphamath.portfolio.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PortfolioPositionRepository extends JpaRepository<PortfolioPositionEntity, String> {
  List<PortfolioPositionEntity> findByAccountIdOrderBySymbolAsc(String accountId);
  Optional<PortfolioPositionEntity> findByAccountIdAndSymbol(String accountId, String symbol);
  void deleteByAccountId(String accountId);
}
