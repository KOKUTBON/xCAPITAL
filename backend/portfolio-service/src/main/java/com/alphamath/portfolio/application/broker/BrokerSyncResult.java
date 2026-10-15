package com.alphamath.portfolio.application.broker;

public record BrokerSyncResult(String connectionId, int accounts, int positions, int orders) {
}
