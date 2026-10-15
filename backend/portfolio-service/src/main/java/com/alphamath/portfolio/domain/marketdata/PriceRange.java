package com.alphamath.portfolio.domain.marketdata;

import java.time.Instant;

public record PriceRange(Instant start, Instant end) {
}
