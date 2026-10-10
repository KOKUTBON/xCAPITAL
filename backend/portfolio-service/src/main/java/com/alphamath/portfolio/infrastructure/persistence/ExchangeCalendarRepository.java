package com.alphamath.portfolio.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ExchangeCalendarRepository extends JpaRepository<ExchangeCalendarEntity, String> {
  List<ExchangeCalendarEntity> findByExchangeCodeOrderBySessionDateAsc(String exchangeCode);

  List<ExchangeCalendarEntity> findByExchangeCodeAndSessionDateBetweenOrderBySessionDateAsc(
      String exchangeCode, LocalDate start, LocalDate end);

  Optional<ExchangeCalendarEntity> findFirstByExchangeCodeAndSessionDateGreaterThanEqualAndStatusOrderBySessionDateAsc(
      String exchangeCode, LocalDate start, String status);

  Optional<ExchangeCalendarEntity> findByExchangeCodeAndSessionDate(String exchangeCode, LocalDate sessionDate);
}
