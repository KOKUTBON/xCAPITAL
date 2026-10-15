package com.alphamath.portfolio.infrastructure.notification;

import com.alphamath.portfolio.application.notification.NotificationProvider;
import com.alphamath.portfolio.application.notification.NotificationProviderResult;
import com.alphamath.portfolio.domain.notification.Notification;
import com.alphamath.portfolio.domain.notification.NotificationChannel;
import com.alphamath.portfolio.domain.notification.NotificationDestination;
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
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class HttpSmsNotificationProvider implements NotificationProvider {
  private static final Logger log = LoggerFactory.getLogger(HttpSmsNotificationProvider.class);

  private final NotificationSmsProperties properties;
  private final RestTemplate restTemplate;
  private final ObjectMapper mapper;

  public HttpSmsNotificationProvider(NotificationSmsProperties properties,
                                     RestTemplateBuilder builder,
                                     ObjectMapper mapper) {
    this.properties = properties;
    Duration connectTimeout = Duration.ofMillis(Math.max(100, properties.getConnectTimeoutMs()));
    Duration readTimeout = Duration.ofMillis(Math.max(100, properties.getReadTimeoutMs()));
    this.restTemplate = builder.setConnectTimeout(connectTimeout).setReadTimeout(readTimeout).build();
    this.mapper = mapper;
  }

  @Override
  public boolean supports(String providerId, NotificationChannel channel) {
    if (channel != NotificationChannel.SMS) {
      return false;
    }
    if (providerId == null || providerId.isBlank()) {
      return false;
    }
    String normalized = providerId.trim().toLowerCase(Locale.US);
    return normalized.equals("http-sms") || normalized.equals("sms-http") || normalized.equals("sms");
  }

  @Override
  public NotificationProviderResult send(NotificationChannel channel,
                                         Notification notification,
                                         NotificationDestination destination,
                                         String providerId) {
    if (!properties.isEnabled()) {
      return NotificationProviderResult.skipped(providerId, "sms disabled");
    }
    if (properties.getBaseUrl() == null || properties.getBaseUrl().isBlank()) {
      return NotificationProviderResult.skipped(providerId, "sms baseUrl not configured");
    }
    if (destination == null || destination.getDestination() == null || destination.getDestination().isBlank()) {
      return NotificationProviderResult.failed(providerId, "sms destination missing");
    }

    URI uri = buildUri(properties.getBaseUrl(), properties.getSendPath());
    if (uri == null) {
      return NotificationProviderResult.failed(providerId, "invalid sms endpoint");
    }

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("id", notification.getId());
    payload.put("type", notification.getType() == null ? null : notification.getType().name());
    payload.put("title", notification.getTitle());
    payload.put("body", notification.getBody());
    payload.put("metadata", notification.getMetadata());
    payload.put("userId", notification.getUserId());
    payload.put("createdAt", notification.getCreatedAt());
    payload.put("channel", channel.name());
    payload.put("to", destination.getDestination());
    payload.put("label", destination.getLabel());

    try {
      String json = mapper.writeValueAsString(payload);
      HttpHeaders headers = buildHeaders(json);
      HttpEntity<String> entity = new HttpEntity<>(json, headers);
      ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.POST, entity, String.class);
      int code = response.getStatusCode().value();
      if (code >= 200 && code < 300) {
        return NotificationProviderResult.sent(providerId);
      }
      if (code == 404 || code == 410) {
        return NotificationProviderResult.bounced(providerId, "sms endpoint not found");
      }
      return NotificationProviderResult.failed(providerId, "sms status " + code);
    } catch (RestClientException e) {
      log.warn("SMS delivery failed: {}", e.getMessage());
      return NotificationProviderResult.failed(providerId, e.getMessage());
    } catch (Exception e) {
      return NotificationProviderResult.failed(providerId, e.getMessage());
    }
  }

  private HttpHeaders buildHeaders(String payload) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    if (properties.getUserAgent() != null && !properties.getUserAgent().isBlank()) {
      headers.set(HttpHeaders.USER_AGENT, properties.getUserAgent().trim());
    }
    if (properties.getApiKey() != null && !properties.getApiKey().isBlank()
        && properties.getApiKeyHeader() != null && !properties.getApiKeyHeader().isBlank()) {
      headers.set(properties.getApiKeyHeader().trim(), properties.getApiKey().trim());
    }
    if (properties.getBearerToken() != null && !properties.getBearerToken().isBlank()) {
      headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getBearerToken().trim());
    } else if (properties.getBasicUser() != null && !properties.getBasicUser().isBlank()) {
      String creds = properties.getBasicUser().trim() + ":" + (properties.getBasicPassword() == null ? "" : properties.getBasicPassword());
      headers.set(HttpHeaders.AUTHORIZATION, "Basic " + Base64.getEncoder().encodeToString(creds.getBytes(StandardCharsets.UTF_8)));
    }
    String signature = sign(payload);
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

  private String sign(String payload) {
    String secret = properties.getSignatureSecret();
    if (secret == null || secret.isBlank()) {
      return null;
    }
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
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
}
