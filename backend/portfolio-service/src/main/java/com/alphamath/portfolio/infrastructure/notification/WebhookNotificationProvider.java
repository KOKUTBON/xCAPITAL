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
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class WebhookNotificationProvider implements NotificationProvider {
  private static final Logger log = LoggerFactory.getLogger(WebhookNotificationProvider.class);

  private final NotificationWebhookProperties properties;
  private final RestTemplate restTemplate;
  private final ObjectMapper mapper;

  public WebhookNotificationProvider(NotificationWebhookProperties properties,
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
    if (channel != NotificationChannel.WEBHOOK) {
      return false;
    }
    if (providerId == null || providerId.isBlank()) {
      return false;
    }
    String normalized = providerId.trim().toLowerCase(Locale.US);
    return normalized.equals("http-webhook") || normalized.equals("webhook-http") || normalized.equals("webhook");
  }

  @Override
  public NotificationProviderResult send(NotificationChannel channel,
                                         Notification notification,
                                         NotificationDestination destination,
                                         String providerId) {
    if (!properties.isEnabled()) {
      return NotificationProviderResult.skipped(providerId, "webhook disabled");
    }
    if (destination == null || destination.getDestination() == null || destination.getDestination().isBlank()) {
      return NotificationProviderResult.failed(providerId, "webhook destination missing");
    }

    URI uri = parseDestination(destination.getDestination());
    if (uri == null) {
      return NotificationProviderResult.failed(providerId, "invalid webhook destination");
    }

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("id", notification.getId());
    payload.put("type", notification.getType() == null ? null : notification.getType().name());
    payload.put("title", notification.getTitle());
    payload.put("body", notification.getBody());
    payload.put("entityType", notification.getEntityType());
    payload.put("entityId", notification.getEntityId());
    payload.put("metadata", notification.getMetadata());
    payload.put("userId", notification.getUserId());
    payload.put("createdAt", notification.getCreatedAt());
    payload.put("channel", channel.name());

    try {
      String json = mapper.writeValueAsString(payload);
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      if (properties.getUserAgent() != null && !properties.getUserAgent().isBlank()) {
        headers.set(HttpHeaders.USER_AGENT, properties.getUserAgent().trim());
      }
      String signature = sign(json);
      String signatureHeader = properties.getSignatureHeader();
      if (signature != null && signatureHeader != null && !signatureHeader.isBlank()) {
        headers.set(signatureHeader.trim(), signature);
      }
      HttpEntity<String> entity = new HttpEntity<>(json, headers);
      ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.POST, entity, String.class);
      int code = response.getStatusCode().value();
      if (code >= 200 && code < 300) {
        return NotificationProviderResult.sent(providerId);
      }
      if (code == 404 || code == 410) {
        return NotificationProviderResult.bounced(providerId, "webhook endpoint not found");
      }
      return NotificationProviderResult.failed(providerId, "webhook status " + code);
    } catch (RestClientException e) {
      log.warn("Webhook delivery failed: {}", e.getMessage());
      return NotificationProviderResult.failed(providerId, e.getMessage());
    } catch (Exception e) {
      return NotificationProviderResult.failed(providerId, e.getMessage());
    }
  }

  private URI parseDestination(String raw) {
    try {
      URI uri = URI.create(raw.trim());
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
