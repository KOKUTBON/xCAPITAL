package com.alphamath.portfolio.infrastructure.reference;

import com.alphamath.portfolio.application.reference.ExchangeCalendarProvider;
import com.alphamath.portfolio.domain.reference.ExchangeCalendarDay;
import com.alphamath.portfolio.domain.reference.ExchangeSessionStatus;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class HttpExchangeCalendarProvider implements ExchangeCalendarProvider {
  private static final Logger log = LoggerFactory.getLogger(HttpExchangeCalendarProvider.class);

  private final ExchangeCalendarHttpProperties properties;
  private final RestTemplateBuilder builder;
  private final ObjectMapper mapper;

  public HttpExchangeCalendarProvider(ExchangeCalendarHttpProperties properties,
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
  public List<ExchangeCalendarDay> fetch(String providerId,
                                         String exchangeCode,
                                         LocalDate start,
                                         LocalDate end,
                                         Map<String, Object> metadata) {
    ExchangeCalendarHttpProperties.ProviderConfig config = provider(providerId);
    URI uri = buildUri(config.getBaseUrl(), config.getCalendarPath(), exchangeCode, start, end);
    if (uri == null) {
      return List.of();
    }
    JsonNode root = executeJson(uri, HttpMethod.GET, null, config);
    JsonNode days = root.path("calendar");
    if (days == null || !days.isArray()) {
      return List.of();
    }
    List<ExchangeCalendarDay> out = new ArrayList<>();
    for (JsonNode row : days) {
      ExchangeCalendarDay day = new ExchangeCalendarDay();
      day.setExchangeCode(exchangeCode);
      day.setSessionDate(readDate(row, "date"));
      day.setStatus(readStatus(row, "status"));
      day.setOpenTime(text(row, "openTime", null));
      day.setCloseTime(text(row, "closeTime", null));
      day.setNotes(text(row, "notes", null));
      if (day.getSessionDate() != null) {
        out.add(day);
      }
    }
    return out;
  }

  private ExchangeCalendarHttpProperties.ProviderConfig provider(String providerId) {
    ExchangeCalendarHttpProperties.ProviderConfig config = properties.provider(providerId);
    if (config == null) {
      throw new IllegalArgumentException("No calendar provider config for " + providerId);
    }
    return config;
  }

  private JsonNode executeJson(URI uri, HttpMethod method, Object payload,
                               ExchangeCalendarHttpProperties.ProviderConfig config) {
    try {
      RestTemplate client = restTemplate(config);
      HttpHeaders headers = buildHeaders(payload, config);
      HttpEntity<?> entity = payload == null ? new HttpEntity<>(headers) : new HttpEntity<>(payload, headers);
      ResponseEntity<String> response = client.exchange(uri, method, entity, String.class);
      if (!response.getStatusCode().is2xxSuccessful()) {
        throw new IllegalStateException("Calendar HTTP status " + response.getStatusCode());
      }
      String body = response.getBody() == null ? "{}" : response.getBody();
      return mapper.readTree(body);
    } catch (RestClientException e) {
      log.warn("Calendar provider request failed: {}", e.getMessage());
      throw new IllegalStateException("Calendar provider request failed");
    } catch (Exception e) {
      throw new IllegalStateException("Calendar provider response parse failed", e);
    }
  }

  private RestTemplate restTemplate(ExchangeCalendarHttpProperties.ProviderConfig config) {
    Duration connectTimeout = Duration.ofMillis(Math.max(100, config.getConnectTimeoutMs()));
    Duration readTimeout = Duration.ofMillis(Math.max(100, config.getReadTimeoutMs()));
    return builder.setConnectTimeout(connectTimeout).setReadTimeout(readTimeout).build();
  }

  private HttpHeaders buildHeaders(Object payload, ExchangeCalendarHttpProperties.ProviderConfig config) {
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

  private URI buildUri(String baseUrl, String path, String exchangeCode, LocalDate start, LocalDate end) {
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
        .queryParam("exchange", exchangeCode);
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

  private LocalDate readDate(JsonNode node, String field) {
    if (node != null && node.has(field) && node.get(field).isTextual()) {
      try {
        return LocalDate.parse(node.get(field).asText());
      } catch (Exception ignored) {
      }
    }
    return null;
  }

  private ExchangeSessionStatus readStatus(JsonNode node, String field) {
    if (node != null && node.has(field) && node.get(field).isTextual()) {
      String raw = node.get(field).asText();
      try {
        return ExchangeSessionStatus.valueOf(raw.trim().toUpperCase(Locale.US));
      } catch (Exception ignored) {
      }
    }
    return ExchangeSessionStatus.OPEN;
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
}
