package com.alphamath.portfolio.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TradeOrderRepository extends JpaRepository<TradeOrderEntity, String> {
  List<TradeOrderEntity> findByProposalIdOrderByCreatedAtAsc(String proposalId);
  void deleteByProposalId(String proposalId);
}
