package com.alphamath.portfolio.infrastructure.broker;

import com.alphamath.portfolio.application.broker.BrokerAdapter;
import com.alphamath.portfolio.application.broker.BrokerSnapshot;
import com.alphamath.portfolio.domain.broker.BrokerConnection;
import com.alphamath.portfolio.domain.broker.BrokerOrder;
import com.alphamath.portfolio.domain.broker.BrokerOrderLeg;
import com.alphamath.portfolio.domain.broker.BrokerOrderLegRequest;
import com.alphamath.portfolio.domain.broker.BrokerOrderPreview;
import com.alphamath.portfolio.domain.broker.BrokerOrderRequest;
import com.alphamath.portfolio.domain.broker.BrokerOrderStatus;
import com.alphamath.portfolio.domain.broker.BrokerPosition;
import com.alphamath.portfolio.domain.execution.AssetClass;
import com.alphamath.portfolio.domain.execution.BrokerAccount;
import com.alphamath.portfolio.domain.execution.BrokerAccountStatus;
import com.alphamath.portfolio.domain.execution.BrokerAccountType;
import com.alphamath.portfolio.domain.execution.OrderType;
import com.alphamath.portfolio.domain.execution.Region;
import com.alphamath.portfolio.domain.execution.TimeInForce;
import com.alphamath.portfolio.domain.trade.TradeSide;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@Order(Ordered.HIGHEST_PRECEDENCE)
public class HttpBrokerAdapter implements BrokerAdapter {
  private static final Logger log = LoggerFactory.getLogger(HttpBrokerAdapter.class);

  private final BrokerHttpProperties properties;
  private final RestTemplateBuilder builder;
  private final ObjectMapper mapper;

  public HttpBrokerAdapter(BrokerHttpProperties properties,
                           RestTemplateBuilder builder,
                           ObjectMapper mapper) {
    this.properties = properties;
    this.builder = builder;
    this.mapper = mapper;
  }

  @Override
  public boolean supports(String brokerId) {
    if (!properties.isEnabled()) {
      return false;
    }
    return properties.provider(brokerId) != null;
  }

  @Override
  public BrokerSnapshot sync(BrokerConnection connection) {
    BrokerHttpProperties.ProviderConfig config = provider(connection.getBrokerId());
    URI uri = buildUri(config.getBaseUrl(), config.getSyncPath());
    if (uri == null) {
      throw new IllegalArgumentException("Broker sync endpoint not configured");
    }
    JsonNode root = executeJson(uri, HttpMethod.GET, null, config, connection);
    List<BrokerAccount> accounts = parseAccounts(root.path("accounts"), connection);
    List<BrokerPosition> positions = parsePositions(root.path("positions"), connection);
    List<BrokerOrder> orders = parseOrders(root.path("orders"), connection);
    return new BrokerSnapshot(accounts, positions, orders);
  }

  @Override
  public BrokerOrder placeOrder(BrokerConnection connection, BrokerOrderRequest request) {
    BrokerHttpProperties.ProviderConfig config = provider(connection.getBrokerId());
    URI uri = buildUri(config.getBaseUrl(), config.getOrdersPath());
    if (uri == null) {
      throw new IllegalArgumentException("Broker orders endpoint not configured");
    }
    Map<String, Object> payload = buildOrderPayload(connection, request);
    JsonNode root = executeJson(uri, HttpMethod.POST, payload, config, connection);
    JsonNode orderNode = root.has("order") ? root.get("order") : root;
    BrokerOrder order = parseOrder(orderNode, connection);
    if (order.getId() == null || order.getId().isBlank()) {
      order.setId(UUID.randomUUID().toString());
    }
    if (order.getExternalOrderId() == null || order.getExternalOrderId().isBlank()) {
    order.setExternalOrderId(request.getClientOrderId());
    }
    order.setClientOrderId(request.getClientOrderId());
    order.setOrderType(request.getOrderType());
    order.setSide(request.getSide());
    order.setTimeInForce(request.getTimeInForce());
    order.setCurrency(request.getCurrency());
    double totalQty = request.getQuantity();
    if (request.getLegs() != null && !request.getLegs().isEmpty()) {
      totalQty = request.getLegs().stream().mapToDouble(BrokerOrderLegRequest::getQuantity).sum();
    }
    order.setTotalQuantity(totalQty);
    order.setSubmittedAt(Instant.now());
    order.setUpdatedAt(Instant.now());
    if (order.getStatus() == null) {
      order.setStatus(BrokerOrderStatus.SUBMITTED);
    }
    return order;
  }

  @Override
  public BrokerOrderPreview previewOrder(BrokerConnection connection, BrokerOrderRequest request) {
    BrokerHttpProperties.ProviderConfig config = provider(connection.getBrokerId());
    if (config.getPreviewPath() == null || config.getPreviewPath().isBlank()) {
      return BrokerAdapter.super.previewOrder(connection, request);
    }
    URI uri = buildUri(config.getBaseUrl(), config.getPreviewPath());
    if (uri == null) {
      return BrokerAdapter.super.previewOrder(connection, request);
    }
    Map<String, Object> payload = buildOrderPayload(connection, request);
    JsonNode root = executeJson(uri, HttpMethod.POST, payload, config, connection);
    BrokerOrderPreview preview = new BrokerOrderPreview();
    preview.setSymbol(text(root, "symbol", request.getSymbol()));
    preview.setAssetClass(readEnum(root, "assetClass", request.getAssetClass(), AssetClass.class));
    preview.setSide(readEnum(root, "side", request.getSide(), TradeSide.class));
    preview.setQuantity(readDouble(root, "quantity", request.getQuantity()));
    preview.setOrderType(readEnum(root, "orderType", request.getOrderType(), OrderType.class));
    preview.setTimeInForce(readEnum(root, "timeInForce", request.getTimeInForce(), TimeInForce.class));
    preview.setPrice(readDouble(root, "price", request.getLimitPrice()));
    preview.setCurrency(text(root, "currency", request.getCurrency()));
    preview.setEstimatedNotional(readDoubleObj(root, "estimatedNotional", null));
    preview.setEstimatedFees(readDoubleObj(root, "estimatedFees", null));
    preview.setEstimatedTotal(readDoubleObj(root, "estimatedTotal", null));
    preview.setMetadata(new LinkedHashMap<>());
    preview.setCreatedAt(Instant.now());
    return preview;
  }

  @Override
  public BrokerOrder refreshOrder(BrokerConnection connection, BrokerOrder order) {
    BrokerHttpProperties.ProviderConfig config = provider(connection.getBrokerId());
    if (config.getRefreshPath() == null || config.getRefreshPath().isBlank()) {
      return BrokerAdapter.super.refreshOrder(connection, order);
    }
    String path = config.getRefreshPath().replace("{orderId}", order.getExternalOrderId() == null ? order.getId() : order.getExternalOrderId());
    URI uri = buildUri(config.getBaseUrl(), path);
    if (uri == null) {
      return order;
    }
    JsonNode root = executeJson(uri, HttpMethod.GET, null, config, connection);
    BrokerOrder refreshed = parseOrder(root, connection);
    if (refreshed.getId() == null) {
      refreshed.setId(order.getId());
    }
    refreshed.setUpdatedAt(Instant.now());
    return refreshed;
  }

  @Override
  public BrokerOrder cancelOrder(BrokerConnection connection, BrokerOrder order) {
    BrokerHttpProperties.ProviderConfig config = provider(connection.getBrokerId());
    if (config.getCancelPath() == null || config.getCancelPath().isBlank()) {
      return BrokerAdapter.super.cancelOrder(connection, order);
    }
    String path = config.getCancelPath().replace("{orderId}", order.getExternalOrderId() == null ? order.getId() : order.getExternalOrderId());
    URI uri = buildUri(config.getBaseUrl(), path);
    if (uri == null) {
      return BrokerAdapter.super.cancelOrder(connection, order);
    }
    JsonNode root = executeJson(uri, HttpMethod.POST, Map.of(), config, connection);
    BrokerOrder canceled = parseOrder(root, connection);
    if (canceled.getId() == null) {
      canceled.setId(order.getId());
    }
    if (canceled.getStatus() == null) {
      canceled.setStatus(BrokerOrderStatus.CANCELED);
    }
    canceled.setUpdatedAt(Instant.now());
    return canceled;
  }

  private Map<String, Object> buildOrderPayload(BrokerConnection connection, BrokerOrderRequest request) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("clientOrderId", request.getClientOrderId());
    payload.put("instrumentId", request.getInstrumentId());
    payload.put("symbol", request.getSymbol());
    payload.put("assetClass", request.getAssetClass() == null ? null : request.getAssetClass().name());
    payload.put("side", request.getSide() == null ? null : request.getSide().name());
    payload.put("quantity", request.getQuantity());
    payload.put("orderType", request.getOrderType() == null ? null : request.getOrderType().name());
    payload.put("timeInForce", request.getTimeInForce() == null ? null : request.getTimeInForce().name());
    payload.put("limitPrice", request.getLimitPrice());
    payload.put("stopPrice", request.getStopPrice());
    payload.put("currency", request.getCurrency());
    payload.put("allowFractional", request.isAllowFractional());
    payload.put("metadata", request.getMetadata() == null ? Map.of() : request.getMetadata());
    payload.put("brokerConnectionId", connection.getId());
    payload.put("brokerId", connection.getBrokerId());
    if (request.getLegs() != null && !request.getLegs().isEmpty()) {
      List<Map<String, Object>> legs = new ArrayList<>();
      for (BrokerOrderLegRequest leg : request.getLegs()) {
        Map<String, Object> legPayload = new LinkedHashMap<>();
        legPayload.put("instrumentId", leg.getInstrumentId());
        legPayload.put("symbol", leg.getSymbol());
        legPayload.put("assetClass", leg.getAssetClass() == null ? null : leg.getAssetClass().name());
        legPayload.put("side", leg.getSide() == null ? null : leg.getSide().name());
        legPayload.put("quantity", leg.getQuantity());
        legPayload.put("limitPrice", leg.getLimitPrice());
        legPayload.put("stopPrice", leg.getStopPrice());
        legPayload.put("metadata", leg.getMetadata() == null ? Map.of() : leg.getMetadata());
        legs.add(legPayload);
      }
      payload.put("legs", legs);
    }
    return payload;
  }

  private BrokerHttpProperties.ProviderConfig provider(String brokerId) {
    BrokerHttpProperties.ProviderConfig config = properties.provider(brokerId);
    if (config == null) {
      throw new IllegalArgumentException("No HTTP broker config for " + brokerId);
    }
    return config;
  }

  private JsonNode executeJson(URI uri, HttpMethod method, Object payload,
                               BrokerHttpProperties.ProviderConfig config,
                               BrokerConnection connection) {
    try {
      RestTemplate client = restTemplate(config);
      HttpHeaders headers = buildHeaders(payload, config, connection);
      HttpEntity<?> entity = payload == null ? new HttpEntity<>(headers) : new HttpEntity<>(payload, headers);
      ResponseEntity<String> response = client.exchange(uri, method, entity, String.class);
      if (!response.getStatusCode().is2xxSuccessful()) {
        throw new IllegalStateException("Broker HTTP status " + response.getStatusCode());
      }
      String body = response.getBody() == null ? "{}" : response.getBody();
      return mapper.readTree(body);
    } catch (RestClientException e) {
      log.warn("Broker HTTP request failed: {}", e.getMessage());
      throw new IllegalStateException("Broker HTTP request failed");
    } catch (Exception e) {
      throw new IllegalStateException("Broker HTTP response parse failed", e);
    }
  }

  private RestTemplate restTemplate(BrokerHttpProperties.ProviderConfig config) {
    Duration connectTimeout = Duration.ofMillis(Math.max(100, config.getConnectTimeoutMs()));
    Duration readTimeout = Duration.ofMillis(Math.max(100, config.getReadTimeoutMs()));
    return builder.setConnectTimeout(connectTimeout).setReadTimeout(readTimeout).build();
  }

  private HttpHeaders buildHeaders(Object payload,
                                   BrokerHttpProperties.ProviderConfig config,
                                   BrokerConnection connection) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    String userAgent = valueOrMeta(config.getUserAgent(), connection, "userAgent");
    if (userAgent != null && !userAgent.isBlank()) {
      headers.set(HttpHeaders.USER_AGENT, userAgent.trim());
    }
    headers.set("X-Broker-Connection-Id", connection.getId());
    String apiKeyHeader = valueOrMeta(config.getApiKeyHeader(), connection, "apiKeyHeader");
    String apiKey = valueOrMeta(config.getApiKey(), connection, "apiKey");
    if (apiKeyHeader != null && !apiKeyHeader.isBlank() && apiKey != null && !apiKey.isBlank()) {
      headers.set(apiKeyHeader.trim(), apiKey.trim());
    }
    String bearer = valueOrMeta(config.getBearerToken(), connection, "bearerToken");
    if (bearer != null && !bearer.isBlank()) {
      headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + bearer.trim());
    } else {
      String basicUser = valueOrMeta(config.getBasicUser(), connection, "basicUser");
      String basicPassword = valueOrMeta(config.getBasicPassword(), connection, "basicPassword");
      if (basicUser != null && !basicUser.isBlank()) {
        String creds = basicUser.trim() + ":" + (basicPassword == null ? "" : basicPassword);
        headers.set(HttpHeaders.AUTHORIZATION, "Basic " + Base64.getEncoder().encodeToString(creds.getBytes(StandardCharsets.UTF_8)));
      }
    }
    String signatureHeader = valueOrMeta(config.getSignatureHeader(), connection, "signatureHeader");
    String signatureSecret = valueOrMeta(config.getSignatureSecret(), connection, "signatureSecret");
    String signature = sign(payload, signatureSecret);
    if (signature != null && signatureHeader != null && !signatureHeader.isBlank()) {
      headers.set(signatureHeader.trim(), signature);
    }
    return headers;
  }

  private String valueOrMeta(String defaultValue, BrokerConnection connection, String key) {
    if (connection == null || connection.getMetadata() == null) {
      return defaultValue;
    }
    Object value = connection.getMetadata().get(key);
    if (value == null) {
      return defaultValue;
    }
    String str = value.toString();
    return str.isBlank() ? defaultValue : str;
  }

  private URI buildUri(String baseUrl, String path) {
    if (baseUrl == null || baseUrl.isBlank()) {
      return null;
    }
    String base = baseUrl.trim();
    String suffix = path == null ? "" : path.trim();
    String url;
    if (suffix.isEmpty()) {
      url = base;
    } else if (base.endsWith("/") && suffix.startsWith("/")) {
      url = base.substring(0, base.length() - 1) + suffix;
    } else if (!base.endsWith("/") && !suffix.startsWith("/")) {
      url = base + "/" + suffix;
    } else {
      url = base + suffix;
    }
    try {
      URI uri = URI.create(url);
      String scheme = uri.getScheme();
      if (scheme == null || scheme.isBlank()) {
        return null;
      }
      String normalized = scheme.toLowerCase(Locale.US);
      if (!normalized.equals("http") && !normalized.equals("https")) {
        return null;
      }
      return uri;
    } catch (Exception e) {
      return null;
    }
  }

  private String sign(Object payload, String secret) {
    if (secret == null || secret.isBlank()) {
      return null;
    }
    try {
      String body = payload == null ? "" : mapper.writeValueAsString(payload);
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] digest = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
      return "sha256=" + hex(digest);
    } catch (Exception e) {
      return null;
    }
  }

  private String hex(byte[] bytes) {
    StringBuilder out = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      out.append(String.format("%02x", b));
    }
    return out.toString();
  }

  private List<BrokerAccount> parseAccounts(JsonNode node, BrokerConnection connection) {
    List<BrokerAccount> out = new ArrayList<>();
    if (node == null || !node.isArray()) {
      return out;
    }
    for (JsonNode acctNode : node) {
      BrokerAccount acct = new BrokerAccount();
      acct.setId(text(acctNode, "id", UUID.randomUUID().toString()));
      acct.setUserId(connection.getUserId());
      acct.setProviderId(connection.getBrokerId());
      acct.setProviderName(text(acctNode, "providerName", connection.getBrokerId()));
      acct.setBrokerConnectionId(connection.getId());
      acct.setExternalAccountId(text(acctNode, "externalAccountId", null));
      acct.setAccountNumber(text(acctNode, "accountNumber", null));
      acct.setBaseCurrency(text(acctNode, "baseCurrency", "USD"));
      acct.setAccountType(readEnum(acctNode, "accountType", BrokerAccountType.CASH, BrokerAccountType.class));
      acct.setRegion(readEnum(acctNode, "region", Region.GLOBAL, Region.class));
      acct.setAssetClasses(readEnumList(acctNode.path("assetClasses"), AssetClass.class));
      acct.setPermissions(readStringList(acctNode.path("permissions")));
      acct.setBalances(readDoubleMap(acctNode.path("balances")));
      acct.setStatus(readEnum(acctNode, "status", BrokerAccountStatus.LINKED, BrokerAccountStatus.class));
      acct.setCreatedAt(Instant.now());
      acct.setUpdatedAt(Instant.now());
      out.add(acct);
    }
    return out;
  }

  private List<BrokerPosition> parsePositions(JsonNode node, BrokerConnection connection) {
    List<BrokerPosition> out = new ArrayList<>();
    if (node == null || !node.isArray()) {
      return out;
    }
    for (JsonNode posNode : node) {
      BrokerPosition pos = new BrokerPosition();
      pos.setId(text(posNode, "id", UUID.randomUUID().toString()));
      pos.setUserId(connection.getUserId());
      pos.setBrokerAccountId(text(posNode, "brokerAccountId", null));
      pos.setSymbol(text(posNode, "symbol", null));
      pos.setAssetClass(readEnum(posNode, "assetClass", AssetClass.EQUITY, AssetClass.class));
      pos.setQuantity(readDouble(posNode, "quantity", 0.0));
      pos.setAvgPrice(readDoubleObj(posNode, "avgPrice", null));
      pos.setMarketPrice(readDoubleObj(posNode, "marketPrice", null));
      pos.setCurrency(text(posNode, "currency", null));
      pos.setUpdatedAt(Instant.now());
      out.add(pos);
    }
    return out;
  }

  private List<BrokerOrder> parseOrders(JsonNode node, BrokerConnection connection) {
    List<BrokerOrder> out = new ArrayList<>();
    if (node == null || !node.isArray()) {
      return out;
    }
    for (JsonNode orderNode : node) {
      out.add(parseOrder(orderNode, connection));
    }
    return out;
  }

  private BrokerOrder parseOrder(JsonNode node, BrokerConnection connection) {
    BrokerOrder order = new BrokerOrder();
    order.setId(text(node, "id", UUID.randomUUID().toString()));
    order.setUserId(connection.getUserId());
    order.setBrokerAccountId(text(node, "brokerAccountId", null));
    order.setExternalOrderId(text(node, "externalOrderId", null));
    order.setClientOrderId(text(node, "clientOrderId", null));
    order.setStatus(readEnum(node, "status", BrokerOrderStatus.SUBMITTED, BrokerOrderStatus.class));
    order.setOrderType(readEnum(node, "orderType", OrderType.MARKET, OrderType.class));
    order.setSide(readEnum(node, "side", TradeSide.BUY, TradeSide.class));
    order.setTimeInForce(readEnum(node, "timeInForce", TimeInForce.DAY, TimeInForce.class));
    order.setSubmittedAt(readInstant(node, "submittedAt", Instant.now()));
    order.setUpdatedAt(readInstant(node, "updatedAt", Instant.now()));
    order.setTotalQuantity(readDouble(node, "totalQuantity", 0.0));
    order.setFilledQuantity(readDouble(node, "filledQuantity", 0.0));
    order.setAvgPrice(readDoubleObj(node, "avgPrice", null));
    order.setCurrency(text(node, "currency", null));
    order.setMetadata(new LinkedHashMap<>());
    order.setLegs(parseOrderLegs(node.path("legs")));
    return order;
  }

  private List<BrokerOrderLeg> parseOrderLegs(JsonNode node) {
    List<BrokerOrderLeg> out = new ArrayList<>();
    if (node == null || !node.isArray()) {
      return out;
    }
    for (JsonNode legNode : node) {
      BrokerOrderLeg leg = new BrokerOrderLeg();
      leg.setId(text(legNode, "id", UUID.randomUUID().toString()));
      leg.setOrderId(text(legNode, "orderId", null));
      leg.setInstrumentId(text(legNode, "instrumentId", null));
      leg.setSymbol(text(legNode, "symbol", null));
      leg.setAssetClass(readEnum(legNode, "assetClass", AssetClass.EQUITY, AssetClass.class));
      leg.setSide(readEnum(legNode, "side", TradeSide.BUY, TradeSide.class));
      leg.setQuantity(readDouble(legNode, "quantity", 0.0));
      leg.setLimitPrice(readDoubleObj(legNode, "limitPrice", null));
      leg.setStopPrice(readDoubleObj(legNode, "stopPrice", null));
      leg.setMetadata(new LinkedHashMap<>());
      out.add(leg);
    }
    return out;
  }

  private String text(JsonNode node, String field, String fallback) {
    if (node != null && node.has(field) && !node.get(field).isNull()) {
      String value = node.get(field).asText();
      if (!value.isBlank()) {
        return value;
      }
    }
    return fallback;
  }

  private double readDouble(JsonNode node, String field, double fallback) {
    if (node != null && node.has(field) && node.get(field).isNumber()) {
      return node.get(field).asDouble();
    }
    return fallback;
  }

  private Double readDoubleObj(JsonNode node, String field, Double fallback) {
    if (node != null && node.has(field) && node.get(field).isNumber()) {
      return node.get(field).asDouble();
    }
    return fallback;
  }

  private Instant readInstant(JsonNode node, String field, Instant fallback) {
    if (node != null && node.has(field)) {
      JsonNode raw = node.get(field);
      if (raw.isNumber()) {
        long epoch = raw.asLong();
        if (epoch > 100000000000L) {
          return Instant.ofEpochMilli(epoch);
        }
        return Instant.ofEpochSecond(epoch);
      }
      if (raw.isTextual()) {
        try {
          return Instant.parse(raw.asText());
        } catch (Exception ignored) {
        }
      }
    }
    return fallback;
  }

  private <T extends Enum<T>> T readEnum(JsonNode node, String field, T fallback, Class<T> type) {
    if (node != null && node.has(field) && node.get(field).isTextual()) {
      String raw = node.get(field).asText();
      try {
        return Enum.valueOf(type, raw.toUpperCase(Locale.US));
      } catch (Exception ignored) {
      }
    }
    return fallback;
  }

  private <T extends Enum<T>> List<T> readEnumList(JsonNode node, Class<T> type) {
    List<T> out = new ArrayList<>();
    if (node == null || !node.isArray()) {
      return out;
    }
    for (JsonNode child : node) {
      if (!child.isTextual()) continue;
      try {
        out.add(Enum.valueOf(type, child.asText().toUpperCase(Locale.US)));
      } catch (Exception ignored) {
      }
    }
    return out;
  }

  private List<String> readStringList(JsonNode node) {
    List<String> out = new ArrayList<>();
    if (node == null || !node.isArray()) {
      return out;
    }
    for (JsonNode child : node) {
      if (child.isTextual()) {
        out.add(child.asText());
      }
    }
    return out;
  }

  private Map<String, Double> readDoubleMap(JsonNode node) {
    Map<String, Double> out = new LinkedHashMap<>();
    if (node == null || !node.isObject()) {
      return out;
    }
    node.fields().forEachRemaining(entry -> {
      if (entry.getValue().isNumber()) {
        out.put(entry.getKey(), entry.getValue().asDouble());
      }
    });
    return out;
  }
}
