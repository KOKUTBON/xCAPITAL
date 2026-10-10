package com.alphamath.portfolio.infrastructure.broker;

import com.alphamath.portfolio.application.broker.BrokerAdapter;
import com.alphamath.portfolio.application.broker.BrokerCatalog;
import com.alphamath.portfolio.application.broker.BrokerSnapshot;
import com.alphamath.portfolio.domain.broker.BrokerConnection;
import com.alphamath.portfolio.domain.broker.BrokerDefinition;
import com.alphamath.portfolio.domain.broker.BrokerOrder;
import com.alphamath.portfolio.domain.broker.BrokerOrderLeg;
import com.alphamath.portfolio.domain.broker.BrokerOrderLegRequest;
import com.alphamath.portfolio.domain.broker.BrokerOrderPreview;
import com.alphamath.portfolio.domain.broker.BrokerOrderRequest;
import com.alphamath.portfolio.domain.broker.BrokerOrderStatus;
import com.alphamath.portfolio.domain.broker.BrokerPosition;
import com.alphamath.portfolio.domain.broker.OptionType;
import com.alphamath.portfolio.domain.execution.AssetClass;
import com.alphamath.portfolio.domain.execution.BrokerAccount;
import com.alphamath.portfolio.domain.execution.BrokerAccountStatus;
import com.alphamath.portfolio.domain.execution.BrokerAccountType;
import com.alphamath.portfolio.domain.execution.Region;
import com.alphamath.portfolio.domain.execution.TimeInForce;
import com.alphamath.portfolio.domain.trade.TradeSide;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Order(Ordered.LOWEST_PRECEDENCE)
public class SimulatedBrokerAdapter implements BrokerAdapter {
  private final BrokerCatalog catalog;

  public SimulatedBrokerAdapter(BrokerCatalog catalog) {
    this.catalog = catalog;
  }

  @Override
  public boolean supports(String brokerId) {
    return true;
  }

  @Override
  public BrokerSnapshot sync(BrokerConnection connection) {
    BrokerDefinition def = catalog.find(connection.getBrokerId());
    if (def == null) {
      throw new IllegalArgumentException("Broker not supported: " + connection.getBrokerId());
    }

    BrokerAccount account = new BrokerAccount();
    account.setId(UUID.randomUUID().toString());
    account.setUserId(connection.getUserId());
    account.setProviderId(def.getId());
    account.setProviderName(def.getName());
    account.setBrokerConnectionId(connection.getId());
    account.setExternalAccountId("ext-" + connection.getId());
    account.setAccountNumber("SIM-" + connection.getId().substring(0, Math.min(8, connection.getId().length())).toUpperCase());
    account.setBaseCurrency("USD");
    account.setAccountType(BrokerAccountType.CASH);
    account.setBalances(Map.of("USD", 250000.0));
    account.setPermissions(List.of("options_level_2", "margin_disabled"));
    account.setRegion(def.getRegions().isEmpty() ? Region.GLOBAL : def.getRegions().get(0));
    account.setAssetClasses(def.getAssetClasses());
    account.setStatus(BrokerAccountStatus.LINKED);
    account.setCreatedAt(Instant.now());
    account.setUpdatedAt(Instant.now());

    List<BrokerPosition> positions = new ArrayList<>();
    for (AssetClass assetClass : def.getAssetClasses()) {
      BrokerPosition pos = samplePosition(connection.getUserId(), account.getId(), assetClass);
      if (pos != null) {
        positions.add(pos);
      }
    }

    return new BrokerSnapshot(List.of(account), positions, List.of());
  }

  @Override
  public BrokerOrder placeOrder(BrokerConnection connection, BrokerOrderRequest request) {
    BrokerOrder order = new BrokerOrder();
    order.setId(UUID.randomUUID().toString());
    order.setUserId(connection.getUserId());
    order.setExternalOrderId(request.getClientOrderId() == null || request.getClientOrderId().isBlank()
        ? "sim-" + order.getId()
        : request.getClientOrderId());
    order.setClientOrderId(request.getClientOrderId());
    order.setStatus(BrokerOrderStatus.SUBMITTED);
    order.setOrderType(request.getOrderType());
    order.setSide(request.getSide());
    order.setTimeInForce(request.getTimeInForce() == null ? TimeInForce.DAY : request.getTimeInForce());
    order.setSubmittedAt(Instant.now());
    order.setUpdatedAt(Instant.now());
    double totalQty = request.getQuantity();
    if (request.getLegs() != null && !request.getLegs().isEmpty()) {
      totalQty = request.getLegs().stream().mapToDouble(BrokerOrderLegRequest::getQuantity).sum();
    }
    order.setTotalQuantity(totalQty);
    order.setFilledQuantity(0.0);
    order.setAvgPrice(null);
    order.setCurrency(request.getCurrency());
    order.setMetadata(new LinkedHashMap<>(request.getMetadata()));

    List<BrokerOrderLeg> legs = new ArrayList<>();
    if (request.getLegs() != null && !request.getLegs().isEmpty()) {
      for (BrokerOrderLegRequest legReq : request.getLegs()) {
        legs.add(toLeg(order.getId(), legReq));
      }
    } else {
      BrokerOrderLeg leg = new BrokerOrderLeg();
      leg.setId(UUID.randomUUID().toString());
      leg.setOrderId(order.getId());
      leg.setInstrumentId(request.getInstrumentId());
      leg.setSymbol(request.getSymbol());
      leg.setAssetClass(request.getAssetClass());
      leg.setSide(request.getSide());
      leg.setQuantity(request.getQuantity());
      leg.setLimitPrice(request.getLimitPrice());
      leg.setStopPrice(request.getStopPrice());
      legs.add(leg);
    }
    order.setLegs(legs);
    return order;
  }

  @Override
  public BrokerOrderPreview previewOrder(BrokerConnection connection, BrokerOrderRequest request) {
    BrokerOrderPreview preview = new BrokerOrderPreview();
    preview.setBrokerId(connection.getBrokerId());
    preview.setSymbol(request.getSymbol());
    preview.setAssetClass(request.getAssetClass());
    preview.setSide(request.getSide());
    preview.setQuantity(request.getQuantity());
    preview.setOrderType(request.getOrderType());
    preview.setTimeInForce(request.getTimeInForce());
    preview.setCurrency(request.getCurrency() == null ? "USD" : request.getCurrency());
    preview.setCreatedAt(Instant.now());

    double qty = request.getQuantity();
    Double price = request.getLimitPrice();
    if (price == null || price <= 0) {
      price = request.getStopPrice();
    }
    if (price == null || price <= 0) {
      preview.getWarnings().add("Missing price; using 0 for estimate.");
      price = 0.0;
    }
    preview.setPrice(price);

    double multiplier = 1.0;
    if (request.getAssetClass() == AssetClass.OPTIONS) {
      multiplier = 100.0;
    } else if (request.getMetadata() != null && request.getMetadata().containsKey("contractMultiplier")) {
      try {
        multiplier = Double.parseDouble(request.getMetadata().get("contractMultiplier").toString());
      } catch (Exception ignored) {
      }
    }

    double notional = Math.max(0.0, qty) * price * multiplier;
    double fees = notional <= 0.0 ? 0.0 : Math.max(1.0, notional * 0.0005);
    preview.setEstimatedNotional(notional);
    preview.setEstimatedFees(fees);
    preview.setEstimatedTotal(notional + fees);
    preview.getMetadata().put("simulated", true);
    return preview;
  }

  @Override
  public BrokerOrder refreshOrder(BrokerConnection connection, BrokerOrder order) {
    if (order.getUpdatedAt() == null) {
      order.setUpdatedAt(Instant.now());
    }
    return order;
  }

  @Override
  public BrokerOrder cancelOrder(BrokerConnection connection, BrokerOrder order) {
    order.setStatus(BrokerOrderStatus.CANCELED);
    order.setUpdatedAt(Instant.now());
    return order;
  }

  private BrokerOrderLeg toLeg(String orderId, BrokerOrderLegRequest req) {
    BrokerOrderLeg leg = new BrokerOrderLeg();
    leg.setId(UUID.randomUUID().toString());
    leg.setOrderId(orderId);
    leg.setInstrumentId(req.getInstrumentId());
    leg.setSymbol(req.getSymbol());
    leg.setAssetClass(req.getAssetClass());
    leg.setSide(req.getSide());
    leg.setQuantity(req.getQuantity());
    leg.setLimitPrice(req.getLimitPrice());
    leg.setStopPrice(req.getStopPrice());
    leg.setOptionType(req.getOptionType());
    leg.setStrike(req.getStrike());
    leg.setExpiry(req.getExpiry());
    leg.setMetadata(new LinkedHashMap<>(req.getMetadata()));
    return leg;
  }

  private BrokerPosition samplePosition(String userId, String accountId, AssetClass assetClass) {
    BrokerPosition pos = new BrokerPosition();
    pos.setId(UUID.randomUUID().toString());
    pos.setUserId(userId);
    pos.setBrokerAccountId(accountId);
    pos.setAssetClass(assetClass);
    pos.setUpdatedAt(Instant.now());

    switch (assetClass) {
      case EQUITY -> {
        return setPosition(pos, "AAPL", 150.0, 40, "USD");
      }
      case ETF -> {
        return setPosition(pos, "SPY", 500.0, 20, "USD");
      }
      case FIXED_INCOME -> {
        return setPosition(pos, "TLT", 90.0, 30, "USD");
      }
      case FX -> {
        return setPosition(pos, "EURUSD", 1.08, 10000, "USD");
      }
      case CRYPTO -> {
        return setPosition(pos, "BTC-USD", 65000.0, 0.5, "USD");
      }
      case OPTIONS -> {
        pos.setSymbol("AAPL_20250117C180");
        pos.setQuantity(2);
        pos.setAvgPrice(4.2);
        pos.setMarketPrice(5.1);
        pos.setMarketValue(5.1 * 2 * 100);
        pos.setCostBasis(4.2 * 2 * 100);
        pos.setUnrealizedPnl(pos.getMarketValue() - pos.getCostBasis());
        pos.setCurrency("USD");
        pos.setMetadata(Map.of(
            "optionType", OptionType.CALL.name(),
            "strike", 180,
            "expiry", LocalDate.now().plusMonths(6).toString()
        ));
        return pos;
      }
      case FUTURES -> {
        return setPosition(pos, "ESZ4", 4800.0, 1, "USD");
      }
      case COMMODITIES -> {
        return setPosition(pos, "GLD", 190.0, 15, "USD");
      }
      case MUTUAL_FUND -> {
        return setPosition(pos, "VFIAX", 400.0, 10, "USD");
      }
      default -> {
        return null;
      }
    }
  }

  private BrokerPosition setPosition(BrokerPosition pos, String symbol, double price, double qty, String currency) {
    pos.setSymbol(symbol);
    pos.setQuantity(qty);
    pos.setAvgPrice(price * 0.95);
    pos.setMarketPrice(price);
    pos.setMarketValue(price * qty);
    pos.setCostBasis(pos.getAvgPrice() * qty);
    pos.setUnrealizedPnl(pos.getMarketValue() - pos.getCostBasis());
    pos.setCurrency(currency);
    return pos;
  }
}
