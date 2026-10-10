package com.alphamath.auth.mfa;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;

@Component
public class TotpService {
  private static final int SECRET_BYTES = 20;
  private final SecureRandom random = new SecureRandom();
  private final MfaProperties properties;
  private final MfaSecretCipher cipher;

  public TotpService(MfaProperties properties, MfaSecretCipher cipher) {
    this.properties = properties;
    this.cipher = cipher;
  }

  public String generateSecret() {
    byte[] bytes = new byte[SECRET_BYTES];
    random.nextBytes(bytes);
    return Base32.encode(bytes);
  }

  public String encryptSecret(String secret) {
    return cipher.encrypt(secret);
  }

  public String decryptSecret(String encrypted) {
    return cipher.decrypt(encrypted);
  }

  public boolean verifyCode(String encryptedSecret, String code) {
    String secret = decryptSecret(encryptedSecret);
    if (secret == null || secret.isBlank()) {
      return false;
    }
    String normalized = normalizeCode(code);
    if (normalized == null) {
      return false;
    }
    int skew = Math.max(0, properties.getSkew());
    long step = properties.getPeriodSeconds();
    long counter = Instant.now().getEpochSecond() / step;
    for (int i = -skew; i <= skew; i++) {
      String candidate = generateCode(secret, counter + i);
      if (candidate.equals(normalized)) {
        return true;
      }
    }
    return false;
  }

  public String otpauthUrl(String email, String secret) {
    String issuer = properties.getIssuer();
    String label = urlEncode(issuer) + ":" + urlEncode(email == null ? "user" : email);
    return "otpauth://totp/" + label
        + "?secret=" + urlEncode(secret)
        + "&issuer=" + urlEncode(issuer)
        + "&algorithm=" + urlEncode(algorithmName())
        + "&digits=" + properties.getDigits()
        + "&period=" + properties.getPeriodSeconds();
  }

  public String issuer() {
    return properties.getIssuer();
  }

  public String maskSecret(String secret) {
    if (secret == null || secret.length() < 4) {
      return "****";
    }
    String tail = secret.substring(secret.length() - 4);
    return "****" + tail;
  }

  private String generateCode(String secret, long counter) {
    byte[] key = Base32.decode(secret);
    byte[] data = new byte[8];
    long value = counter;
    for (int i = 7; i >= 0; i--) {
      data[i] = (byte) (value & 0xff);
      value >>= 8;
    }
    try {
      Mac mac = Mac.getInstance(properties.getAlgorithm());
      mac.init(new SecretKeySpec(key, properties.getAlgorithm()));
      byte[] hash = mac.doFinal(data);
      int offset = hash[hash.length - 1] & 0xf;
      int binary = ((hash[offset] & 0x7f) << 24)
          | ((hash[offset + 1] & 0xff) << 16)
          | ((hash[offset + 2] & 0xff) << 8)
          | (hash[offset + 3] & 0xff);
      int mod = (int) Math.pow(10, properties.getDigits());
      int otp = binary % mod;
      return String.format(Locale.US, "%0" + properties.getDigits() + "d", otp);
    } catch (Exception e) {
      return "";
    }
  }

  private String normalizeCode(String code) {
    if (code == null) {
      return null;
    }
    String digits = code.replaceAll("[^0-9]", "");
    if (digits.length() != properties.getDigits()) {
      return null;
    }
    return digits;
  }

  private String algorithmName() {
    String alg = properties.getAlgorithm();
    if (alg == null || alg.isBlank()) {
      return "SHA1";
    }
    String upper = alg.trim().toUpperCase(Locale.US);
    if (upper.endsWith("SHA256")) return "SHA256";
    if (upper.endsWith("SHA512")) return "SHA512";
    return "SHA1";
  }

  private String urlEncode(String value) {
    if (value == null) {
      return "";
    }
    return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
  }
}
