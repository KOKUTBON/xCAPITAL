package com.alphamath.portfolio.infrastructure.persistence;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CorporateActionRepository extends JpaRepository<CorporateActionEntity, String> {
  List<CorporateActionEntity> findByUserIdOrderByCreatedAtDesc(String userId, PageRequest page);
  List<CorporateActionEntity> findByUserIdAndSymbolOrderByCreatedAtDesc(String userId, String symbol, PageRequest page);

  List<CorporateActionEntity> findByUserIdAndOrgIdOrderByCreatedAtDesc(String userId, String orgId, PageRequest page);
  List<CorporateActionEntity> findByUserIdAndOrgIdAndSymbolOrderByCreatedAtDesc(String userId, String orgId, String symbol,
                                                                              PageRequest page);

  long countByOrgId(String orgId);
}
