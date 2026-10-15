package com.alphamath.portfolio.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BankingAccountRepository extends JpaRepository<BankingAccountEntity, String> {
  Optional<BankingAccountEntity> findByUserId(String userId);
  Optional<BankingAccountEntity> findByUserIdAndOrgId(String userId, String orgId);

  long countByOrgId(String orgId);

  @Query("select coalesce(sum(a.cash), 0) from BankingAccountEntity a where a.orgId = :orgId")
  Double sumCashByOrgId(@Param("orgId") String orgId);
}
