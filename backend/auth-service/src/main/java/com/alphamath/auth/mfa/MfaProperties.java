package com.alphamath.auth.mfa;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "alphamath.auth.mfa")
public class MfaProperties {
  private String issuer = "xCAPITAL";
  private String secretKey;
  private int digits = 6;
  private int periodSeconds = 30;
  private int skew = 1;
  private String algorithm = "HmacSHA1";

  public String getIssuer() {
    return issuer;
  }

  public void setIssuer(String issuer) {
    this.issuer = issuer;
  }

  public String getSecretKey() {
    return secretKey;
  }

  public void setSecretKey(String secretKey) {
    this.secretKey = secretKey;
  }

  public int getDigits() {
    return digits;
  }

  public void setDigits(int digits) {
    this.digits = digits;
  }

  public int getPeriodSeconds() {
    return periodSeconds;
  }

  public void setPeriodSeconds(int periodSeconds) {
    this.periodSeconds = periodSeconds;
  }

  public int getSkew() {
    return skew;
  }

  public void setSkew(int skew) {
    this.skew = skew;
  }

  public String getAlgorithm() {
    return algorithm;
  }

  public void setAlgorithm(String algorithm) {
    this.algorithm = algorithm;
  }
}
