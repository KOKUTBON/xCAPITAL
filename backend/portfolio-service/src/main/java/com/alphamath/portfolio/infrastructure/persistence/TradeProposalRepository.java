package com.alphamath.portfolio.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

import java.util.List;

public interface TradeProposalRepository extends JpaRepository<TradeProposalEntity, String> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from TradeProposalEntity p where p.id = :id")
  Optional<TradeProposalEntity> findByIdForUpdate(@Param("id") String id);
  List<TradeProposalEntity> findByUserIdOrderByCreatedAtDesc(String userId);
  List<TradeProposalEntity> findByUserIdAndOrgIdOrderByCreatedAtDesc(String userId, String orgId);
}
