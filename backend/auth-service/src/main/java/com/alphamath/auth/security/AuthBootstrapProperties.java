package com.alphamath.auth.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
@ConfigurationProperties(prefix = "alphamath.auth")
public class AuthBootstrapProperties {
  private List<String> bootstrapAdmins = new ArrayList<>();

  public List<String> getBootstrapAdmins() {
    return bootstrapAdmins;
  }

  public void setBootstrapAdmins(List<String> bootstrapAdmins) {
    this.bootstrapAdmins = bootstrapAdmins;
  }

  public boolean isBootstrapAdmin(String email) {
    if (email == null || email.isBlank()) {
      return false;
    }
    if (bootstrapAdmins == null || bootstrapAdmins.isEmpty()) {
      return false;
    }
    String needle = email.trim().toLowerCase(Locale.US);
    for (String entry : bootstrapAdmins) {
      if (entry == null || entry.isBlank()) {
        continue;
      }
      if (needle.equals(entry.trim().toLowerCase(Locale.US))) {
        return true;
      }
    }
    return false;
  }
}
