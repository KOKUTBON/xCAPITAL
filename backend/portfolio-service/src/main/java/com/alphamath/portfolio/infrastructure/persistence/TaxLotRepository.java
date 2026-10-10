package com.alphamath.portfolio.infrastructure.persistence;

import com.alphamath.portfolio.domain.reporting.TaxLotStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaxLotRepository extends JpaRepository<TaxLotEntity, String> {
  List<TaxLotEntity> findByUserIdAndAccountIdOrderByUpdatedAtDesc(String userId, String accountId, PageRequest page);
  List<TaxLotEntity> findByUserIdAndAccountIdOrderByUpdatedAtDesc(String userId, String accountId);
  List<TaxLotEntity> findByUserIdAndAccountIdAndStatusOrderByUpdatedAtDesc(String userId, String accountId,
                                                                          TaxLotStatus status, PageRequest page);
  List<TaxLotEntity> findByUserIdAndAccountIdAndSymbolOrderByUpdatedAtDesc(String userId, String accountId,
                                                                          String symbol, PageRequest page);
  void deleteByUserIdAndAccountId(String userId, String accountId);

  List<TaxLotEntity> findByUserIdAndOrgIdAndAccountIdOrderByUpdatedAtDesc(String userId, String orgId, String accountId,
                                                                        PageRequest page);
  List<TaxLotEntity> findByUserIdAndOrgIdAndAccountIdOrderByUpdatedAtDesc(String userId, String orgId, String accountId);
  List<TaxLotEntity> findByUserIdAndOrgIdAndAccountIdAndStatusOrderByUpdatedAtDesc(String userId, String orgId, String accountId,
                                                                                  TaxLotStatus status, PageRequest page);
  List<TaxLotEntity> findByUserIdAndOrgIdAndAccountIdAndSymbolOrderByUpdatedAtDesc(String userId, String orgId, String accountId,
                                                                                  String symbol, PageRequest page);
  void deleteByUserIdAndOrgIdAndAccountId(String userId, String orgId, String accountId);

  long countByOrgId(String orgId);
}
