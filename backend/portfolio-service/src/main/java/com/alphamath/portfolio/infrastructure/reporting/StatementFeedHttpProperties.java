package com.alphamath.portfolio.infrastructure.reporting;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "alphamath.statements.http")
public class StatementFeedHttpProperties {
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

  public ProviderConfig provider(String providerId) {
    if (providerId == null) {
      return null;
    }
    return providers.get(providerId);
  }

  public static class ProviderConfig {
    private String baseUrl;
    private String feedPath = "/statements/ledger";
    private String apiKeyHeader = "X-API-KEY";
    private String apiKey;
    private String bearerToken;
    private String basicUser;
    private String basicPassword;
    private String signatureHeader = "X-Webhook-Signature";
    private String signatureSecret = "";
    private String userAgent = "xCAPITAL-Statements/1.0";
    private int connectTimeoutMs = 2000;
    private int readTimeoutMs = 5000;

    public String getBaseUrl() {
      return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
    }

    public String getFeedPath() {
      return feedPath;
    }

    public void setFeedPath(String feedPath) {
      this.feedPath = feedPath;
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
