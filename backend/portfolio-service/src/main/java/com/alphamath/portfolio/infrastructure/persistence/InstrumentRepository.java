package com.alphamath.portfolio.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InstrumentRepository extends JpaRepository<InstrumentEntity, String> {
  List<InstrumentEntity> findBySymbolContainingIgnoreCase(String symbol);

  InstrumentEntity findFirstBySymbolIgnoreCase(String symbol);
}
