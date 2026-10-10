package com.alphamath.portfolio.web;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "alphamath.security")
public class SecurityPolicyProperties {
  private MfaPolicy mfa = new MfaPolicy();
  private RbacPolicy rbac = new RbacPolicy();

  public MfaPolicy getMfa() {
    return mfa;
  }

  public void setMfa(MfaPolicy mfa) {
    this.mfa = mfa;
  }

  public RbacPolicy getRbac() {
    return rbac;
  }

  public void setRbac(RbacPolicy rbac) {
    this.rbac = rbac;
  }

  public static class MfaPolicy {
    private boolean enforce = false;

    public boolean isEnforce() {
      return enforce;
    }

    public void setEnforce(boolean enforce) {
      this.enforce = enforce;
    }
  }

  public static class RbacPolicy {
    private boolean enforce = false;

    public boolean isEnforce() {
      return enforce;
    }

    public void setEnforce(boolean enforce) {
      this.enforce = enforce;
    }
  }
}
