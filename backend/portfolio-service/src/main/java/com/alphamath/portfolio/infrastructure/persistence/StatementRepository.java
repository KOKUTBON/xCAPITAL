package com.alphamath.portfolio.infrastructure.persistence;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StatementRepository extends JpaRepository<StatementEntity, String> {
  List<StatementEntity> findByUserIdAndAccountIdOrderByPeriodEndDesc(String userId, String accountId, PageRequest page);

  List<StatementEntity> findByUserIdAndOrgIdAndAccountIdOrderByPeriodEndDesc(String userId, String orgId, String accountId,
                                                                            PageRequest page);

  long countByOrgId(String orgId);
}
