package com.alphamath.portfolio.infrastructure.notification;

import com.alphamath.portfolio.application.notification.NotificationProvider;
import com.alphamath.portfolio.application.notification.NotificationProviderResult;
import com.alphamath.portfolio.domain.notification.Notification;
import com.alphamath.portfolio.domain.notification.NotificationChannel;
import com.alphamath.portfolio.domain.notification.NotificationDestination;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Properties;

@Service
public class SmtpEmailNotificationProvider implements NotificationProvider {
  private static final Logger log = LoggerFactory.getLogger(SmtpEmailNotificationProvider.class);

  private final NotificationSmtpProperties properties;

  public SmtpEmailNotificationProvider(NotificationSmtpProperties properties) {
    this.properties = properties;
  }

  @Override
  public boolean supports(String providerId, NotificationChannel channel) {
    if (channel != NotificationChannel.EMAIL) {
      return false;
    }
    if (providerId == null || providerId.isBlank()) {
      return false;
    }
    String normalized = providerId.trim().toLowerCase(Locale.US);
    return normalized.equals("smtp-email") || normalized.equals("smtp");
  }

  @Override
  public NotificationProviderResult send(NotificationChannel channel,
                                         Notification notification,
                                         NotificationDestination destination,
                                         String providerId) {
    if (!properties.isEnabled()) {
      return NotificationProviderResult.skipped(providerId, "smtp disabled");
    }
    if (properties.getHost() == null || properties.getHost().isBlank()) {
      return NotificationProviderResult.skipped(providerId, "smtp host not configured");
    }
    if (destination == null || destination.getDestination() == null || destination.getDestination().isBlank()) {
      return NotificationProviderResult.failed(providerId, "email destination missing");
    }
    String email = destination.getDestination().trim();
    if (!email.contains("@")) {
      return NotificationProviderResult.failed(providerId, "invalid email destination");
    }

    try {
      JavaMailSenderImpl sender = buildSender();
      MimeMessage message = sender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());

      String fromAddress = safeFromAddress();
      String fromName = properties.getFromName();
      if (fromName != null && !fromName.isBlank()) {
        helper.setFrom(fromAddress, fromName.trim());
      } else {
        helper.setFrom(fromAddress);
      }
      helper.setTo(email);

      String subject = notification.getTitle();
      if (subject == null || subject.isBlank()) {
        subject = "xCAPITAL notification";
      }
      helper.setSubject(subject);

      String body = buildBody(notification);
      helper.setText(body, false);

      sender.send(message);
      return NotificationProviderResult.sent(providerId);
    } catch (Exception e) {
      log.warn("SMTP notification failed: {}", e.getMessage());
      return NotificationProviderResult.failed(providerId, e.getMessage());
    }
  }

  private JavaMailSenderImpl buildSender() {
    JavaMailSenderImpl sender = new JavaMailSenderImpl();
    sender.setHost(properties.getHost().trim());
    sender.setPort(properties.getPort());
    if (properties.getUsername() != null && !properties.getUsername().isBlank()) {
      sender.setUsername(properties.getUsername());
    }
    if (properties.getPassword() != null && !properties.getPassword().isBlank()) {
      sender.setPassword(properties.getPassword());
    }
    sender.setDefaultEncoding(StandardCharsets.UTF_8.name());

    Properties javaMail = sender.getJavaMailProperties();
    javaMail.put("mail.smtp.auth", String.valueOf(authEnabled()));
    javaMail.put("mail.smtp.starttls.enable", String.valueOf(properties.isStartTls()));
    javaMail.put("mail.smtp.connectiontimeout", String.valueOf(properties.getConnectTimeoutMs()));
    javaMail.put("mail.smtp.timeout", String.valueOf(properties.getReadTimeoutMs()));
    javaMail.put("mail.smtp.writetimeout", String.valueOf(properties.getReadTimeoutMs()));
    return sender;
  }

  private String safeFromAddress() {
    String fromAddress = properties.getFromAddress();
    if (fromAddress == null || fromAddress.isBlank()) {
      return "noreply@xcapital.local";
    }
    return fromAddress.trim();
  }

  private boolean authEnabled() {
    String username = properties.getUsername();
    String password = properties.getPassword();
    return username != null && !username.isBlank() && password != null && !password.isBlank();
  }

  private String buildBody(Notification notification) {
    StringBuilder out = new StringBuilder();
    if (notification.getBody() != null && !notification.getBody().isBlank()) {
      out.append(notification.getBody().trim());
    } else if (notification.getTitle() != null && !notification.getTitle().isBlank()) {
      out.append(notification.getTitle().trim());
    } else {
      out.append("You have a new notification.");
    }
    if (notification.getEntityType() != null || notification.getEntityId() != null) {
      out.append("\n\nEntity: ");
      if (notification.getEntityType() != null) {
        out.append(notification.getEntityType());
      }
      if (notification.getEntityId() != null) {
        out.append(" (").append(notification.getEntityId()).append(")");
      }
    }
    return out.toString();
  }
}
