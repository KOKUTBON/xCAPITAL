package com.alphamath.portfolio.infrastructure.reporting;

import com.alphamath.portfolio.application.reporting.StatementFeedProvider;
import com.alphamath.portfolio.domain.reporting.LedgerEntryRequest;
import com.alphamath.portfolio.domain.reporting.LedgerEntryType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

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

@Service
public class HttpStatementFeedProvider implements StatementFeedProvider {
  private static final Logger log = LoggerFactory.getLogger(HttpStatementFeedProvider.class);

  private final StatementFeedHttpProperties properties;
  private final RestTemplateBuilder builder;
  private final ObjectMapper mapper;

  public HttpStatementFeedProvider(StatementFeedHttpProperties properties,
                                   RestTemplateBuilder builder,
                                   ObjectMapper mapper) {
    this.properties = properties;
    this.builder = builder;
    this.mapper = mapper;
  }

  @Override
  public boolean supports(String providerId) {
    if (!properties.isEnabled()) {
      return false;
    }
    return properties.provider(providerId) != null;
  }

  @Override
  public List<String> providerIds() {
    if (!properties.isEnabled()) {
      return List.of();
    }
    return new ArrayList<>(properties.getProviders().keySet());
  }

  @Override
  public List<LedgerEntryRequest> fetch(String providerId,
                                        String accountId,
                                        Instant start,
                                        Instant end,
                                        Map<String, Object> metadata) {
    StatementFeedHttpProperties.ProviderConfig config = provider(providerId);
    URI uri = buildUri(config.getBaseUrl(), config.getFeedPath(), accountId, start, end);
    if (uri == null) {
      return List.of();
    }
    JsonNode root = executeJson(uri, HttpMethod.GET, null, config);
    JsonNode entries = root.path("entries");
    if (entries == null || !entries.isArray()) {
      return List.of();
    }
    List<LedgerEntryRequest> out = new ArrayList<>();
    for (JsonNode row : entries) {
      LedgerEntryRequest entry = new LedgerEntryRequest();
      entry.setAccountId(accountId);
      entry.setEntryType(mapType(text(row, "type", null), readDouble(row, "quantity", null), readDouble(row, "amount", null)).name());
      entry.setSymbol(text(row, "symbol", null));
      entry.setQuantity(readDouble(row, "quantity", null));
      entry.setPrice(readDouble(row, "price", null));
      entry.setAmount(readDouble(row, "amount", null));
      entry.setCurrency(text(row, "currency", null));
      entry.setDescription(text(row, "description", null));
      entry.setTradeDate(readInstant(row, "timestamp", null));
      entry.setMetadata(Map.of("source", text(row, "source", "statement_feed")));
      out.add(entry);
    }
    return out;
  }

  private StatementFeedHttpProperties.ProviderConfig provider(String providerId) {
    StatementFeedHttpProperties.ProviderConfig config = properties.provider(providerId);
    if (config == null) {
      throw new IllegalArgumentException("No statement feed provider config for " + providerId);
    }
    return config;
  }

  private JsonNode executeJson(URI uri, HttpMethod method, Object payload,
                               StatementFeedHttpProperties.ProviderConfig config) {
    try {
      RestTemplate client = restTemplate(config);
      HttpHeaders headers = buildHeaders(payload, config);
      HttpEntity<?> entity = payload == null ? new HttpEntity<>(headers) : new HttpEntity<>(payload, headers);
      ResponseEntity<String> response = client.exchange(uri, method, entity, String.class);
      if (!response.getStatusCode().is2xxSuccessful()) {
        throw new IllegalStateException("Statement feed HTTP status " + response.getStatusCode());
      }
      String body = response.getBody() == null ? "{}" : response.getBody();
      return mapper.readTree(body);
    } catch (RestClientException e) {
      log.warn("Statement feed request failed: {}", e.getMessage());
      throw new IllegalStateException("Statement feed request failed");
    } catch (Exception e) {
      throw new IllegalStateException("Statement feed response parse failed", e);
    }
  }

  private RestTemplate restTemplate(StatementFeedHttpProperties.ProviderConfig config) {
    Duration connectTimeout = Duration.ofMillis(Math.max(100, config.getConnectTimeoutMs()));
    Duration readTimeout = Duration.ofMillis(Math.max(100, config.getReadTimeoutMs()));
    return builder.setConnectTimeout(connectTimeout).setReadTimeout(readTimeout).build();
  }

  private HttpHeaders buildHeaders(Object payload, StatementFeedHttpProperties.ProviderConfig config) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    if (config.getUserAgent() != null && !config.getUserAgent().isBlank()) {
      headers.set(HttpHeaders.USER_AGENT, config.getUserAgent().trim());
    }
    String apiKeyHeader = config.getApiKeyHeader();
    String apiKey = config.getApiKey();
    if (apiKeyHeader != null && !apiKeyHeader.isBlank() && apiKey != null && !apiKey.isBlank()) {
      headers.set(apiKeyHeader.trim(), apiKey.trim());
    }
    if (config.getBearerToken() != null && !config.getBearerToken().isBlank()) {
      headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + config.getBearerToken().trim());
    } else if (config.getBasicUser() != null && !config.getBasicUser().isBlank()) {
      String creds = config.getBasicUser().trim() + ":" + (config.getBasicPassword() == null ? "" : config.getBasicPassword());
      headers.set(HttpHeaders.AUTHORIZATION, "Basic " + Base64.getEncoder().encodeToString(creds.getBytes(StandardCharsets.UTF_8)));
    }
    String signature = sign(payload, config.getSignatureSecret());
    String signatureHeader = config.getSignatureHeader();
    if (signature != null && signatureHeader != null && !signatureHeader.isBlank()) {
      headers.set(signatureHeader.trim(), signature);
    }
    return headers;
  }

  private URI buildUri(String baseUrl, String path, String accountId, Instant start, Instant end) {
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
    UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url)
        .queryParam("accountId", accountId);
    if (start != null) {
      builder.queryParam("start", start.toString());
    }
    if (end != null) {
      builder.queryParam("end", end.toString());
    }
    return builder.build().toUri();
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

  private String text(JsonNode node, String field, String fallback) {
    if (node != null && node.has(field) && !node.get(field).isNull()) {
      String value = node.get(field).asText();
      if (!value.isBlank()) {
        return value;
      }
    }
    return fallback;
  }

  private Double readDouble(JsonNode node, String field, Double fallback) {
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

  private LedgerEntryType mapType(String rawType, Double qty, Double amount) {
    if (rawType != null) {
      String normalized = rawType.trim().toLowerCase(Locale.US);
      if (normalized.contains("buy")) return LedgerEntryType.BUY;
      if (normalized.contains("sell")) return LedgerEntryType.SELL;
      if (normalized.contains("dividend")) return LedgerEntryType.DIVIDEND;
      if (normalized.contains("fee")) return LedgerEntryType.FEE;
      if (normalized.contains("interest")) return LedgerEntryType.INTEREST;
      if (normalized.contains("deposit")) return LedgerEntryType.DEPOSIT;
      if (normalized.contains("withdraw")) return LedgerEntryType.WITHDRAWAL;
      if (normalized.contains("transfer")) return LedgerEntryType.TRANSFER;
      if (normalized.contains("fx")) return LedgerEntryType.FX;
    }
    if (qty != null && qty != 0.0) {
      if (amount != null && amount < 0.0) {
        return LedgerEntryType.SELL;
      }
      return LedgerEntryType.BUY;
    }
    if (amount != null && amount < 0.0) {
      return LedgerEntryType.WITHDRAWAL;
    }
    if (amount != null && amount > 0.0) {
      return LedgerEntryType.DEPOSIT;
    }
    return LedgerEntryType.ADJUSTMENT;
  }
}
