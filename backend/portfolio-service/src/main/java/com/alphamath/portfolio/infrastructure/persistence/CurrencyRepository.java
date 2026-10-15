package com.alphamath.portfolio.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CurrencyRepository extends JpaRepository<CurrencyEntity, String> {
}
