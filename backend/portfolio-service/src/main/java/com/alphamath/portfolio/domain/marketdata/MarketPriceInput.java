package com.alphamath.portfolio.domain.marketdata;

public record MarketPriceInput(String symbol, String timestamp, Double open, Double high, Double low, Double close, Double volume) {
}
