package com.alphamath.portfolio.application.broker;

import com.alphamath.portfolio.domain.broker.BrokerOrder;
import com.alphamath.portfolio.domain.broker.BrokerPosition;
import com.alphamath.portfolio.domain.execution.BrokerAccount;

import java.util.List;

public record BrokerSnapshot(List<BrokerAccount> accounts,
                             List<BrokerPosition> positions,
                             List<BrokerOrder> orders) {
}
