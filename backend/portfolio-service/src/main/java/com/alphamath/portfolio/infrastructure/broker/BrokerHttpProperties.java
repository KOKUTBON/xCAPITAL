package com.alphamath.portfolio.infrastructure.broker;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "alphamath.brokers.http")
public class BrokerHttpProperties {
  private boolean enabled = false;
  private Map<String, ProviderConfig> providers = new LinkedHashMap<>();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public Map<String, ProviderConfig> getProviders() {
    return providers;
  }

  public void setProviders(Map<String, ProviderConfig> providers) {
    this.providers = providers == null ? new LinkedHashMap<>() : providers;
  }

  public ProviderConfig provider(String brokerId) {
    if (brokerId == null) {
      return null;
    }
    return providers.get(brokerId);
  }

  public static class ProviderConfig {
    private String baseUrl;
    private String syncPath = "/sync";
    private String ordersPath = "/orders";
    private String previewPath = "/orders/preview";
    private String refreshPath = "/orders/{orderId}";
    private String cancelPath = "/orders/{orderId}/cancel";
    private String apiKeyHeader = "X-API-KEY";
    private String apiKey;
    private String bearerToken;
    private String basicUser;
    private String basicPassword;
    private String signatureHeader = "X-Webhook-Signature";
    private String signatureSecret = "";
    private String userAgent = "xCAPITAL-Broker/1.0";
    private int connectTimeoutMs = 2000;
    private int readTimeoutMs = 5000;

    public String getBaseUrl() {
      return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
    }

    public String getSyncPath() {
      return syncPath;
    }

    public void setSyncPath(String syncPath) {
      this.syncPath = syncPath;
    }

    public String getOrdersPath() {
      return ordersPath;
    }

    public void setOrdersPath(String ordersPath) {
      this.ordersPath = ordersPath;
    }

    public String getPreviewPath() {
      return previewPath;
    }

    public void setPreviewPath(String previewPath) {
      this.previewPath = previewPath;
    }

    public String getRefreshPath() {
      return refreshPath;
    }

    public void setRefreshPath(String refreshPath) {
      this.refreshPath = refreshPath;
    }

    public String getCancelPath() {
      return cancelPath;
    }

    public void setCancelPath(String cancelPath) {
      this.cancelPath = cancelPath;
    }

    public String getApiKeyHeader() {
      return apiKeyHeader;
    }

    public void setApiKeyHeader(String apiKeyHeader) {
      this.apiKeyHeader = apiKeyHeader;
    }

    public String getApiKey() {
      return apiKey;
    }

    public void setApiKey(String apiKey) {
      this.apiKey = apiKey;
    }

    public String getBearerToken() {
      return bearerToken;
    }

    public void setBearerToken(String bearerToken) {
      this.bearerToken = bearerToken;
    }

    public String getBasicUser() {
      return basicUser;
    }

    public void setBasicUser(String basicUser) {
      this.basicUser = basicUser;
    }

    public String getBasicPassword() {
      return basicPassword;
    }

    public void setBasicPassword(String basicPassword) {
      this.basicPassword = basicPassword;
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
  }
}
