package com.alphamath.portfolio.infrastructure.funding;

import com.alphamath.portfolio.application.funding.FundingAdapter;
import com.alphamath.portfolio.domain.execution.BrokerAccount;
import com.alphamath.portfolio.domain.funding.FundingDepositReceipt;
import com.alphamath.portfolio.domain.funding.FundingDepositRequest;
import com.alphamath.portfolio.domain.funding.FundingSource;
import com.alphamath.portfolio.domain.funding.FundingTransactionStatus;
import com.alphamath.portfolio.domain.funding.FundingTransferReceipt;
import com.alphamath.portfolio.domain.funding.FundingTransferRequest;
import com.alphamath.portfolio.domain.funding.FundingWithdrawalReceipt;
import com.alphamath.portfolio.domain.funding.FundingWithdrawalRequest;
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
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@Order(Ordered.HIGHEST_PRECEDENCE)
public class HttpFundingAdapter implements FundingAdapter {
  private static final Logger log = LoggerFactory.getLogger(HttpFundingAdapter.class);

  private final FundingHttpProperties properties;
  private final RestTemplateBuilder builder;
  private final ObjectMapper mapper;

  public HttpFundingAdapter(FundingHttpProperties properties,
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
  public FundingDepositReceipt deposit(FundingSource source, FundingDepositRequest request) {
    FundingHttpProperties.ProviderConfig config = provider(source.getProviderId());
    URI uri = buildUri(config.getBaseUrl(), config.getDepositPath());
    if (uri == null) {
      throw new IllegalArgumentException("Funding deposit endpoint not configured");
    }
    Map<String, Object> payload = basePayload(source, request.getAmount());
    JsonNode root = executeJson(uri, HttpMethod.POST, payload, config, source);
    FundingDepositReceipt receipt = new FundingDepositReceipt();
    receipt.setId(text(root, "id", UUID.randomUUID().toString()));
    receipt.setSourceId(source.getId());
    receipt.setAmount(readDouble(root, "amount", request.getAmount() == null ? 0.0 : request.getAmount()));
    receipt.setCurrency(text(root, "currency", source.getCurrency()));
    receipt.setStatus(text(root, "status", FundingTransactionStatus.PENDING.name()));
    receipt.setProviderId(source.getProviderId());
    receipt.setProviderReference(text(root, "providerReference", null));
    receipt.setNote(text(root, "note", null));
    receipt.setCreatedAt(Instant.now());
    receipt.setUpdatedAt(Instant.now());
    return receipt;
  }

  @Override
  public FundingWithdrawalReceipt withdraw(FundingSource source, FundingWithdrawalRequest request) {
    FundingHttpProperties.ProviderConfig config = provider(source.getProviderId());
    URI uri = buildUri(config.getBaseUrl(), config.getWithdrawalPath());
    if (uri == null) {
      throw new IllegalArgumentException("Funding withdrawal endpoint not configured");
    }
    Map<String, Object> payload = basePayload(source, request.getAmount());
    JsonNode root = executeJson(uri, HttpMethod.POST, payload, config, source);
    FundingWithdrawalReceipt receipt = new FundingWithdrawalReceipt();
    receipt.setId(text(root, "id", UUID.randomUUID().toString()));
    receipt.setSourceId(source.getId());
    receipt.setAmount(readDouble(root, "amount", request.getAmount() == null ? 0.0 : request.getAmount()));
    receipt.setCurrency(text(root, "currency", source.getCurrency()));
    receipt.setStatus(text(root, "status", FundingTransactionStatus.PENDING.name()));
    receipt.setProviderId(source.getProviderId());
    receipt.setProviderReference(text(root, "providerReference", null));
    receipt.setNote(text(root, "note", null));
    receipt.setCreatedAt(Instant.now());
    receipt.setUpdatedAt(Instant.now());
    return receipt;
  }

  @Override
  public FundingTransferReceipt transfer(FundingSource source, BrokerAccount brokerAccount, FundingTransferRequest request) {
    FundingHttpProperties.ProviderConfig config = provider(source.getProviderId());
    URI uri = buildUri(config.getBaseUrl(), config.getTransferPath());
    if (uri == null) {
      throw new IllegalArgumentException("Funding transfer endpoint not configured");
    }
    Map<String, Object> payload = basePayload(source, request.getAmount());
    payload.put("brokerAccountId", brokerAccount.getId());
    payload.put("direction", request.getDirection() == null ? null : request.getDirection().name());
    payload.put("reference", request.getReference());
    payload.put("currency", request.getCurrency() == null ? source.getCurrency() : request.getCurrency());
    JsonNode root = executeJson(uri, HttpMethod.POST, payload, config, source);
    FundingTransferReceipt receipt = new FundingTransferReceipt();
    receipt.setId(text(root, "id", UUID.randomUUID().toString()));
    receipt.setSourceId(source.getId());
    receipt.setBrokerAccountId(brokerAccount.getId());
    receipt.setDirection(request.getDirection());
    receipt.setAmount(readDouble(root, "amount", request.getAmount() == null ? 0.0 : request.getAmount()));
    receipt.setCurrency(text(root, "currency", source.getCurrency()));
    receipt.setStatus(text(root, "status", FundingTransactionStatus.PENDING.name()));
    receipt.setProviderId(source.getProviderId());
    receipt.setProviderReference(text(root, "providerReference", null));
    receipt.setNote(text(root, "note", null));
    receipt.setCreatedAt(Instant.now());
    receipt.setUpdatedAt(Instant.now());
    return receipt;
  }

  private Map<String, Object> basePayload(FundingSource source, Double amount) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("sourceId", source.getId());
    payload.put("providerId", source.getProviderId());
    payload.put("providerReference", source.getProviderReference());
    payload.put("methodType", source.getMethodType() == null ? null : source.getMethodType().name());
    payload.put("amount", amount == null ? 0.0 : amount);
    payload.put("currency", source.getCurrency());
    payload.put("label", source.getLabel());
    payload.put("last4", source.getLast4());
    return payload;
  }

  private FundingHttpProperties.ProviderConfig provider(String providerId) {
    FundingHttpProperties.ProviderConfig config = properties.provider(providerId);
    if (config == null) {
      throw new IllegalArgumentException("No HTTP funding config for " + providerId);
    }
    return config;
  }

  private JsonNode executeJson(URI uri, HttpMethod method, Object payload,
                               FundingHttpProperties.ProviderConfig config,
                               FundingSource source) {
    try {
      RestTemplate client = restTemplate(config);
      HttpHeaders headers = buildHeaders(payload, config, source);
      HttpEntity<?> entity = payload == null ? new HttpEntity<>(headers) : new HttpEntity<>(payload, headers);
      ResponseEntity<String> response = client.exchange(uri, method, entity, String.class);
      if (!response.getStatusCode().is2xxSuccessful()) {
        throw new IllegalStateException("Funding HTTP status " + response.getStatusCode());
      }
      String body = response.getBody() == null ? "{}" : response.getBody();
      return mapper.readTree(body);
    } catch (RestClientException e) {
      log.warn("Funding HTTP request failed: {}", e.getMessage());
      throw new IllegalStateException("Funding HTTP request failed");
    } catch (Exception e) {
      throw new IllegalStateException("Funding HTTP response parse failed", e);
    }
  }

  private RestTemplate restTemplate(FundingHttpProperties.ProviderConfig config) {
    Duration connectTimeout = Duration.ofMillis(Math.max(100, config.getConnectTimeoutMs()));
    Duration readTimeout = Duration.ofMillis(Math.max(100, config.getReadTimeoutMs()));
    return builder.setConnectTimeout(connectTimeout).setReadTimeout(readTimeout).build();
  }

  private HttpHeaders buildHeaders(Object payload,
                                   FundingHttpProperties.ProviderConfig config,
                                   FundingSource source) {
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
    headers.set("X-Funding-Source-Id", source.getId());
    return headers;
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
}
