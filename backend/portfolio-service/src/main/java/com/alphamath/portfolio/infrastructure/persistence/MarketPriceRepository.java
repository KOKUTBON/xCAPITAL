package com.alphamath.portfolio.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface MarketPriceRepository extends JpaRepository<MarketPriceEntity, String> {
  List<MarketPriceEntity> findBySymbolAndTsBetweenOrderByTsAsc(String symbol, Instant start, Instant end);

  List<MarketPriceEntity> findBySymbolOrderByTsAsc(String symbol);

  MarketPriceEntity findTopBySymbolOrderByTsDesc(String symbol);

  @Query("select distinct m.symbol from MarketPriceEntity m order by m.symbol")
  List<String> findDistinctSymbols();
}
