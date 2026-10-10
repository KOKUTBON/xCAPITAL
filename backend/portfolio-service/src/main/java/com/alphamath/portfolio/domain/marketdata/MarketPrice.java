package com.alphamath.portfolio.domain.marketdata;

import java.time.Instant;

public record MarketPrice(String symbol, Instant timestamp, double open, double high, double low, double close, Double volume, String source) {
}
