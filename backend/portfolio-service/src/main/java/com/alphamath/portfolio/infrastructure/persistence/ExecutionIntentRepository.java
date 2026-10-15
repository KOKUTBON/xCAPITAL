package com.alphamath.portfolio.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import com.alphamath.portfolio.domain.execution.ExecutionStatus;

import java.util.List;

public interface ExecutionIntentRepository extends JpaRepository<ExecutionIntentEntity, String> {
  @Modifying
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  @Query("update ExecutionIntentEntity i set i.status = :next where i.id = :id and i.status = :expected "
      + "and i.userId = :userId and ((:orgId is null and i.orgId is null) or i.orgId = :orgId) "
      + "and i.approvalHash = :approvalHash")
  int claimSubmission(@Param("id") String id, @Param("userId") String userId,
      @Param("orgId") String orgId, @Param("approvalHash") String approvalHash,
      @Param("expected") ExecutionStatus expected, @Param("next") ExecutionStatus next);

  @Modifying
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  @Query("update ExecutionIntentEntity i set i.status = :next where i.id = :id and i.status = :expected")
  int markSubmissionUnknown(@Param("id") String id, @Param("expected") ExecutionStatus expected,
      @Param("next") ExecutionStatus next);
  List<ExecutionIntentEntity> findByUserIdOrderByCreatedAtDesc(String userId);
  List<ExecutionIntentEntity> findByUserIdAndOrgIdOrderByCreatedAtDesc(String userId, String orgId);
}
