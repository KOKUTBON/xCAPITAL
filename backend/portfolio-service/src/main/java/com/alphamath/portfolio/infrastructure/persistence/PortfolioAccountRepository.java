package com.alphamath.portfolio.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PortfolioAccountRepository extends JpaRepository<PortfolioAccountEntity, String> {
  Optional<PortfolioAccountEntity> findByUserIdAndAccountType(String userId, String accountType);
  Optional<PortfolioAccountEntity> findByUserIdAndOrgIdAndAccountType(String userId, String orgId, String accountType);

  long countByOrgId(String orgId);

  @Query("select coalesce(sum(a.cash), 0) from PortfolioAccountEntity a where a.orgId = :orgId")
  Double sumCashByOrgId(@Param("orgId") String orgId);
}
