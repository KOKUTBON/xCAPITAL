package com.alphamath.portfolio.web;

import com.alphamath.portfolio.application.broker.BrokerIntegrationService;
import com.alphamath.portfolio.application.broker.BrokerRoutingService;
import com.alphamath.portfolio.application.broker.BrokerSyncResult;
import com.alphamath.portfolio.domain.broker.BrokerConnection;
import com.alphamath.portfolio.domain.broker.BrokerDefinition;
import com.alphamath.portfolio.domain.broker.BrokerOrder;
import com.alphamath.portfolio.domain.broker.BrokerOrderPreview;
import com.alphamath.portfolio.domain.broker.BrokerOrderRequest;
import com.alphamath.portfolio.domain.broker.BrokerOrderReview;
import com.alphamath.portfolio.domain.broker.BrokerRecommendation;
import com.alphamath.portfolio.domain.broker.BrokerPosition;
import com.alphamath.portfolio.domain.execution.BrokerAccount;
import com.alphamath.portfolio.domain.execution.AssetClass;
import com.alphamath.portfolio.domain.execution.OrderType;
import com.alphamath.portfolio.domain.execution.Region;
import com.alphamath.portfolio.domain.execution.TimeInForce;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/brokers")
public class BrokerIntegrationController {
  private final BrokerIntegrationService brokers;
  private final BrokerRoutingService routing;
  private final SecurityGuard security;

  public BrokerIntegrationController(BrokerIntegrationService brokers,
                                     BrokerRoutingService routing,
                                     SecurityGuard security) {
    this.brokers = brokers;
    this.routing = routing;
    this.security = security;
  }

  @GetMapping
  public List<BrokerDefinition> listBrokers() {
    return brokers.listBrokers();
  }

  @PostMapping("/{brokerId}/connections")
  public BrokerConnection connect(@RequestHeader(value = "X-User-Mfa", required = false) String mfa,
                                  @PathVariable String brokerId,
                                  @RequestBody ConnectRequest req,
                                  Principal principal) {
    security.requireMfa(mfa, "broker connection");
    return brokers.connect(userId(principal), brokerId, req == null ? null : req.label,
        req == null ? null : req.metadata);
  }

  @GetMapping("/connections")
  public List<BrokerConnection> listConnections(Principal principal) {
    return brokers.listConnections(userId(principal));
  }

  @PostMapping("/connections/{id}/sync")
  public BrokerSyncResult sync(@PathVariable String id, Principal principal) {
    return brokers.syncConnection(userId(principal), id);
  }

  @GetMapping("/accounts")
  public List<BrokerAccount> listAccounts(Principal principal) {
    return brokers.listAccounts(userId(principal));
  }

  @PostMapping("/recommend")
  public List<BrokerRecommendation> recommend(@RequestBody BrokerRecommendationRequest req, Principal principal) {
    AssetClass assetClass = parseAssetClass(req.assetClass);
    Region region = parseRegion(req.region);
    OrderType orderType = parseOrderType(req.orderType);
    TimeInForce tif = parseTimeInForce(req.timeInForce);
    return routing.recommend(userId(principal), assetClass, region, orderType, tif, req.currency);
  }

  @GetMapping("/accounts/{id}/positions")
  public List<BrokerPosition> positions(@PathVariable String id, Principal principal) {
    return brokers.listPositions(userId(principal), id);
  }

  @GetMapping("/accounts/{id}/orders")
  public List<BrokerOrder> orders(@PathVariable String id, Principal principal) {
    return brokers.listOrders(userId(principal), id);
  }

  @PostMapping("/accounts/{id}/orders")
  public BrokerOrder placeOrder(@RequestHeader(value = "X-User-Mfa", required = false) String mfa,
                                @PathVariable String id,
                                @RequestBody BrokerOrderRequest req,
                                Principal principal) {
    security.requireMfa(mfa, "broker order placement");
    return brokers.placeOrder(userId(principal), id, req);
  }

  @PostMapping("/accounts/{id}/orders/preview")
  public BrokerOrderPreview previewOrder(@PathVariable String id, @RequestBody BrokerOrderRequest req, Principal principal) {
    return brokers.previewOrder(userId(principal), id, req);
  }

  @PostMapping("/accounts/{id}/orders/review")
  public BrokerOrderReview reviewOrder(@PathVariable String id,
                                       @RequestBody OrderReviewRequest req,
                                       Principal principal) {
    if (req == null || req.order == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "order is required");
    }
    return brokers.reviewOrder(userId(principal), id, req.order, req.aiHorizon, req.lookback, req.includeCompliance);
  }

  @PostMapping("/accounts/{id}/orders/{orderId}/refresh")
  public BrokerOrder refreshOrder(@PathVariable String id, @PathVariable String orderId, Principal principal) {
    return brokers.refreshOrder(userId(principal), id, orderId);
  }

  @PostMapping("/accounts/{id}/orders/{orderId}/cancel")
  public BrokerOrder cancelOrder(@RequestHeader(value = "X-User-Mfa", required = false) String mfa,
                                 @PathVariable String id,
                                 @PathVariable String orderId,
                                 Principal principal) {
    security.requireMfa(mfa, "broker order cancel");
    return brokers.cancelOrder(userId(principal), id, orderId);
  }

  private String userId(Principal principal) {
    return principal == null ? "unknown" : principal.getName();
  }

  private AssetClass parseAssetClass(String raw) {
    if (raw == null || raw.isBlank()) return null;
    return AssetClass.valueOf(raw.trim().toUpperCase());
  }

  private Region parseRegion(String raw) {
    if (raw == null || raw.isBlank()) return null;
    return Region.valueOf(raw.trim().toUpperCase());
  }

  private OrderType parseOrderType(String raw) {
    if (raw == null || raw.isBlank()) return null;
    return OrderType.valueOf(raw.trim().toUpperCase());
  }

  private TimeInForce parseTimeInForce(String raw) {
    if (raw == null || raw.isBlank()) return null;
    return TimeInForce.valueOf(raw.trim().toUpperCase());
  }

  public static class ConnectRequest {
    public String label;
    public Map<String, Object> metadata;
  }

  public static class BrokerRecommendationRequest {
    public String assetClass;
    public String region;
    public String orderType;
    public String timeInForce;
    public String currency;
  }

  public static class OrderReviewRequest {
    public BrokerOrderRequest order;
    public Integer aiHorizon = 1;
    public Integer lookback = 120;
    public boolean includeCompliance = true;
  }
}
