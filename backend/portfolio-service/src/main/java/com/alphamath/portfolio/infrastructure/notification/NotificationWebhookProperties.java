package com.alphamath.portfolio.infrastructure.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "alphamath.notifications.webhook")
public class NotificationWebhookProperties {
  private boolean enabled = false;
  private int connectTimeoutMs = 2000;
  private int readTimeoutMs = 5000;
  private String signatureHeader = "X-Webhook-Signature";
  private String signatureSecret = "";
  private String userAgent = "xCAPITAL-Notifier/1.0";

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public int getConnectTimeoutMs() {
    return connectTimeoutMs;
  }

  public void setConnectTimeoutMs(int connectTimeoutMs) {
    this.connectTimeoutMs = connectTimeoutMs;
  }

  public int getReadTimeoutMs() {
    return readTimeoutMs;
  }

  public void setReadTimeoutMs(int readTimeoutMs) {
    this.readTimeoutMs = readTimeoutMs;
  }

  public String getSignatureHeader() {
    return signatureHeader;
  }

  public void setSignatureHeader(String signatureHeader) {
    this.signatureHeader = signatureHeader;
  }

  public String getSignatureSecret() {
    return signatureSecret;
  }

  public void setSignatureSecret(String signatureSecret) {
    this.signatureSecret = signatureSecret;
  }

  public String getUserAgent() {
    return userAgent;
  }

  public void setUserAgent(String userAgent) {
    this.userAgent = userAgent;
  }
}
