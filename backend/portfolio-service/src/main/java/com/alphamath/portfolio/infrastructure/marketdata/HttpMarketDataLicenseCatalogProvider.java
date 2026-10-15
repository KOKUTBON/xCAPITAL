package com.alphamath.portfolio.infrastructure.marketdata;

import com.alphamath.portfolio.application.marketdata.MarketDataLicenseCatalogProvider;
import com.alphamath.portfolio.domain.execution.AssetClass;
import com.alphamath.portfolio.domain.execution.Region;
import com.alphamath.portfolio.domain.marketdata.MarketDataLicenseCatalogEntry;
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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

@Service
public class HttpMarketDataLicenseCatalogProvider implements MarketDataLicenseCatalogProvider {
  private static final Logger log = LoggerFactory.getLogger(HttpMarketDataLicenseCatalogProvider.class);

  private final MarketDataLicenseCatalogHttpProperties properties;
  private final RestTemplateBuilder builder;
  private final ObjectMapper mapper;

  public HttpMarketDataLicenseCatalogProvider(MarketDataLicenseCatalogHttpProperties properties,
                                              RestTemplateBuilder builder,
                                              ObjectMapper mapper) {
    this.properties = properties;
    this.builder = builder;
    this.mapper = mapper;
  }

  @Override
  public boolean enabled() {
    return properties.isEnabled();
  }

  @Override
  public List<MarketDataLicenseCatalogEntry> listCatalog() {
    if (!properties.isEnabled()) {
      return List.of();
    }
    URI uri = buildUri(properties.getBaseUrl(), properties.getCatalogPath());
    if (uri == null) {
      return List.of();
    }
    JsonNode root = executeJson(uri, HttpMethod.GET, null);
    JsonNode entries = root.path("licenses");
    if (entries == null || !entries.isArray()) {
      return List.of();
    }
    List<MarketDataLicenseCatalogEntry> out = new ArrayList<>();
    for (JsonNode row : entries) {
      MarketDataLicenseCatalogEntry entry = new MarketDataLicenseCatalogEntry();
      entry.setProvider(text(row, "provider", null));
      entry.setPlan(text(row, "plan", null));
      entry.setAssetClasses(readAssetClasses(row.path("assetClasses")));
      entry.setExchanges(readStringList(row.path("exchanges")));
      entry.setRegions(readRegions(row.path("regions")));
      entry.setNotes(text(row, "notes", null));
      out.add(entry);
    }
    return out;
  }

  private JsonNode executeJson(URI uri, HttpMethod method, Object payload) {
    try {
      RestTemplate client = restTemplate();
      HttpHeaders headers = buildHeaders(payload);
      HttpEntity<?> entity = payload == null ? new HttpEntity<>(headers) : new HttpEntity<>(payload, headers);
      ResponseEntity<String> response = client.exchange(uri, method, entity, String.class);
      if (!response.getStatusCode().is2xxSuccessful()) {
        throw new IllegalStateException("License catalog status " + response.getStatusCode());
      }
      String body = response.getBody() == null ? "{}" : response.getBody();
      return mapper.readTree(body);
    } catch (RestClientException e) {
      log.warn("License catalog request failed: {}", e.getMessage());
      return mapper.createObjectNode();
    } catch (Exception e) {
      return mapper.createObjectNode();
    }
  }

  private RestTemplate restTemplate() {
    Duration connectTimeout = Duration.ofMillis(Math.max(100, properties.getConnectTimeoutMs()));
    Duration readTimeout = Duration.ofMillis(Math.max(100, properties.getReadTimeoutMs()));
    return builder.setConnectTimeout(connectTimeout).setReadTimeout(readTimeout).build();
  }

  private HttpHeaders buildHeaders(Object payload) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    if (properties.getUserAgent() != null && !properties.getUserAgent().isBlank()) {
      headers.set(HttpHeaders.USER_AGENT, properties.getUserAgent().trim());
    }
    String apiKeyHeader = properties.getApiKeyHeader();
    String apiKey = properties.getApiKey();
    if (apiKeyHeader != null && !apiKeyHeader.isBlank() && apiKey != null && !apiKey.isBlank()) {
      headers.set(apiKeyHeader.trim(), apiKey.trim());
    }
    if (properties.getBearerToken() != null && !properties.getBearerToken().isBlank()) {
      headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getBearerToken().trim());
    } else if (properties.getBasicUser() != null && !properties.getBasicUser().isBlank()) {
      String creds = properties.getBasicUser().trim() + ":" + (properties.getBasicPassword() == null ? "" : properties.getBasicPassword());
      headers.set(HttpHeaders.AUTHORIZATION, "Basic " + Base64.getEncoder().encodeToString(creds.getBytes(StandardCharsets.UTF_8)));
    }
    String signature = sign(payload, properties.getSignatureSecret());
    String signatureHeader = properties.getSignatureHeader();
    if (signature != null && signatureHeader != null && !signatureHeader.isBlank()) {
      headers.set(signatureHeader.trim(), signature);
    }
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

  private List<AssetClass> readAssetClasses(JsonNode node) {
    List<AssetClass> out = new ArrayList<>();
    if (node == null || !node.isArray()) {
      return out;
    }
    for (JsonNode child : node) {
      if (child.isTextual()) {
        try {
          out.add(AssetClass.valueOf(child.asText().toUpperCase(Locale.US)));
        } catch (Exception ignored) {
        }
      }
    }
    return out;
  }

  private List<Region> readRegions(JsonNode node) {
    List<Region> out = new ArrayList<>();
    if (node == null || !node.isArray()) {
      return out;
    }
    for (JsonNode child : node) {
      if (child.isTextual()) {
        try {
          out.add(Region.valueOf(child.asText().toUpperCase(Locale.US)));
        } catch (Exception ignored) {
        }
      }
    }
    return out;
  }
}
