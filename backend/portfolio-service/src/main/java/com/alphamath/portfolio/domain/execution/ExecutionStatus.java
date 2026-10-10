package com.alphamath.portfolio.domain.execution;

public enum ExecutionStatus {
  AUTHORIZED,
  SUBMITTING,
  SUBMISSION_UNKNOWN,
  SUBMITTED,
  ACCEPTED,
  REJECTED,
  PARTIALLY_FILLED,
  FILLED,
  FAILED
}
