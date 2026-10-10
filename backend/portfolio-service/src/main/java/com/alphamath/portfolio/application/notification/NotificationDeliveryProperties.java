package com.alphamath.portfolio.application.notification;

import com.alphamath.portfolio.domain.notification.NotificationChannel;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "alphamath.notifications.delivery")
public class NotificationDeliveryProperties {
  private boolean enabled = true;
  private long pollDelayMs = 10000;
  private int maxAttempts = 5;
  private long baseDelaySeconds = 30;
  private long maxDelaySeconds = 3600;
  private int batchSize = 100;
  private ChannelSettings email = new ChannelSettings(true, "smtp-email");
  private ChannelSettings sms = new ChannelSettings(true, "http-sms");
  private ChannelSettings push = new ChannelSettings(true, "http-push");
  private ChannelSettings webhook = new ChannelSettings(true, "http-webhook");

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public long getPollDelayMs() {
    return pollDelayMs;
  }

  public void setPollDelayMs(long pollDelayMs) {
    this.pollDelayMs = pollDelayMs;
  }

  public int getMaxAttempts() {
    return maxAttempts;
  }

  public void setMaxAttempts(int maxAttempts) {
    this.maxAttempts = maxAttempts;
  }

  public long getBaseDelaySeconds() {
    return baseDelaySeconds;
  }

  public void setBaseDelaySeconds(long baseDelaySeconds) {
    this.baseDelaySeconds = baseDelaySeconds;
  }

  public long getMaxDelaySeconds() {
    return maxDelaySeconds;
  }

  public void setMaxDelaySeconds(long maxDelaySeconds) {
    this.maxDelaySeconds = maxDelaySeconds;
  }

  public int getBatchSize() {
    return batchSize;
  }

  public void setBatchSize(int batchSize) {
    this.batchSize = batchSize;
  }

  public ChannelSettings getEmail() {
    return email;
  }

  public void setEmail(ChannelSettings email) {
    this.email = email;
  }

  public ChannelSettings getSms() {
    return sms;
  }

  public void setSms(ChannelSettings sms) {
    this.sms = sms;
  }

  public ChannelSettings getPush() {
    return push;
  }

  public void setPush(ChannelSettings push) {
    this.push = push;
  }

  public ChannelSettings getWebhook() {
    return webhook;
  }

  public void setWebhook(ChannelSettings webhook) {
    this.webhook = webhook;
  }

  public ChannelSettings settingsFor(NotificationChannel channel) {
    if (channel == null) {
      return new ChannelSettings(false, "disabled");
    }
    return switch (channel) {
      case EMAIL -> email;
      case SMS -> sms;
      case PUSH -> push;
      case WEBHOOK -> webhook;
      case IN_APP -> new ChannelSettings(true, "internal");
    };
  }

  public static class ChannelSettings {
    private boolean enabled;
    private String provider;

    public ChannelSettings() {
      this(true, "stub");
    }

    public ChannelSettings(boolean enabled, String provider) {
      this.enabled = enabled;
      this.provider = provider;
    }

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public String getProvider() {
      return provider;
    }

    public void setProvider(String provider) {
      this.provider = provider;
    }
  }
}
