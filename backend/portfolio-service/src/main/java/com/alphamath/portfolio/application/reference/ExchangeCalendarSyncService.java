package com.alphamath.portfolio.application.reference;

import com.alphamath.portfolio.domain.reference.ExchangeCalendarDay;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ExchangeCalendarSyncService {
  private final ReferenceDataService reference;
  private final List<ExchangeCalendarProvider> providers;

  public ExchangeCalendarSyncService(ReferenceDataService reference,
                                     List<ExchangeCalendarProvider> providers) {
    this.reference = reference;
    this.providers = providers == null ? List.of() : providers;
  }

  public List<ExchangeCalendarDay> sync(String exchangeCode,
                                        String providerId,
                                        LocalDate start,
                                        LocalDate end,
                                        Map<String, Object> metadata) {
    ExchangeCalendarProvider provider = providerFor(providerId);
    List<ExchangeCalendarDay> days = provider.fetch(providerId, exchangeCode, start, end, metadata);
    if (days.isEmpty()) {
      return List.of();
    }
    return reference.upsertExchangeCalendar(exchangeCode, days);
  }

  public List<String> listProviders() {
    List<String> out = new ArrayList<>();
    for (ExchangeCalendarProvider provider : providers) {
      out.addAll(provider.providerIds());
    }
    return out;
  }

  private ExchangeCalendarProvider providerFor(String providerId) {
    for (ExchangeCalendarProvider provider : providers) {
      if (provider.supports(providerId)) {
        return provider;
      }
    }
    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Calendar provider not found");
  }
}
