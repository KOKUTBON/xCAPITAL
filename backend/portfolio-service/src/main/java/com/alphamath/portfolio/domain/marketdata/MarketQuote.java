package com.alphamath.portfolio.domain.marketdata;

import java.time.Instant;

public record MarketQuote(String symbol, Instant timestamp, double price, String source) {
}
