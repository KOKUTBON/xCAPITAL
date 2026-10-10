package com.alphamath.portfolio.application.reference;

import com.alphamath.portfolio.domain.reference.ExchangeCalendarDay;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface ExchangeCalendarProvider {
  boolean supports(String providerId);

  List<ExchangeCalendarDay> fetch(String providerId,
                                  String exchangeCode,
                                  LocalDate start,
                                  LocalDate end,
                                  Map<String, Object> metadata);

  default List<String> providerIds() {
    return List.of();
  }
}
