package com.alphamath.portfolio.application.reporting;

import com.alphamath.portfolio.domain.reporting.LedgerEntryRequest;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface StatementFeedProvider {
  boolean supports(String providerId);

  List<LedgerEntryRequest> fetch(String providerId,
                                 String accountId,
                                 Instant start,
                                 Instant end,
                                 Map<String, Object> metadata);

  default List<String> providerIds() {
    return List.of();
  }
}
