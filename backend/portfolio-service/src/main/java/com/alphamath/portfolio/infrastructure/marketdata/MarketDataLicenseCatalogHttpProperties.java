package com.alphamath.portfolio.infrastructure.marketdata;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "alphamath.marketdata.license-catalog.http")
public class MarketDataLicenseCatalogHttpProperties {
  private boolean enabled = false;
  private String baseUrl;
  private String catalogPath = "/licenses/catalog";
  private String apiKeyHeader = "X-API-KEY";
  private String apiKey;
  private String bearerToken;
  private String basicUser;
  private String basicPassword;
  private String signatureHeader = "X-Webhook-Signature";
  private String signatureSecret = "";
  private String userAgent = "xCAPITAL-MarketData/1.0";
  private int connectTimeoutMs = 2000;
  private int readTimeoutMs = 5000;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public String getCatalogPath() {
    return catalogPath;
  }

  public void setCatalogPath(String catalogPath) {
    this.catalogPath = catalogPath;
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
