package com.alphamath.auth.mfa;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class MfaSecretCipher {
  private static final Logger log = LoggerFactory.getLogger(MfaSecretCipher.class);
  private static final String PREFIX = "enc:v1:";
  private static final int IV_BYTES = 12;
  private static final int TAG_BITS = 128;

  private final MfaProperties properties;
  private final SecureRandom random = new SecureRandom();

  public MfaSecretCipher(MfaProperties properties) {
    this.properties = properties;
  }

  public String encrypt(String plaintext) {
    if (plaintext == null || plaintext.isBlank()) {
      return plaintext;
    }
    SecretKey key = deriveKey();
    byte[] iv = new byte[IV_BYTES];
    random.nextBytes(iv);
    try {
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
      byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
      byte[] combined = new byte[iv.length + encrypted.length];
      System.arraycopy(iv, 0, combined, 0, iv.length);
      System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
      return PREFIX + Base64.getEncoder().encodeToString(combined);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to encrypt MFA secret", e);
    }
  }

  public String decrypt(String ciphertext) {
    if (ciphertext == null || ciphertext.isBlank()) {
      return ciphertext;
    }
    if (!ciphertext.startsWith(PREFIX)) {
      return ciphertext;
    }
    String payload = ciphertext.substring(PREFIX.length());
    byte[] combined;
    try {
      combined = Base64.getDecoder().decode(payload);
    } catch (IllegalArgumentException e) {
      log.warn("Invalid MFA secret encoding");
      return null;
    }
    if (combined.length <= IV_BYTES) {
      log.warn("Invalid MFA secret payload");
      return null;
    }
    byte[] iv = new byte[IV_BYTES];
    byte[] encrypted = new byte[combined.length - IV_BYTES];
    System.arraycopy(combined, 0, iv, 0, iv.length);
    System.arraycopy(combined, iv.length, encrypted, 0, encrypted.length);
    try {
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, deriveKey(), new GCMParameterSpec(TAG_BITS, iv));
      byte[] plaintext = cipher.doFinal(encrypted);
      return new String(plaintext, StandardCharsets.UTF_8);
    } catch (Exception e) {
      log.warn("Failed to decrypt MFA secret: {}", e.getMessage());
      return null;
    }
  }

  private SecretKey deriveKey() {
    String raw = properties.getSecretKey();
    if (raw == null || raw.isBlank()) {
      throw new IllegalStateException("MFA secret key not configured");
    }
    try {
      byte[] bytes = raw.getBytes(StandardCharsets.UTF_8);
      if (bytes.length == 16 || bytes.length == 24 || bytes.length == 32) {
        return new SecretKeySpec(bytes, "AES");
      }
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(bytes);
      return new SecretKeySpec(hash, "AES");
    } catch (Exception e) {
      throw new IllegalStateException("Failed to derive MFA secret key", e);
    }
  }
}
