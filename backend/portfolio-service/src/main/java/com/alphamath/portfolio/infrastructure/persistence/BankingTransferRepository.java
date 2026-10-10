package com.alphamath.portfolio.infrastructure.persistence;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BankingTransferRepository extends JpaRepository<BankingTransferEntity, String> {
  List<BankingTransferEntity> findByUserIdOrderByCreatedAtDesc(String userId, PageRequest page);
  List<BankingTransferEntity> findByUserIdAndOrgIdOrderByCreatedAtDesc(String userId, String orgId, PageRequest page);
}
