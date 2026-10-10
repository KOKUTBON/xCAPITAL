package com.alphamath.auth.mfa;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

final class Base32 {
  private static final char[] ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
  private static final int[] LOOKUP = new int[128];

  static {
    Arrays.fill(LOOKUP, -1);
    for (int i = 0; i < ALPHABET.length; i++) {
      LOOKUP[ALPHABET[i]] = i;
    }
  }

  private Base32() {}

  static String encode(byte[] data) {
    if (data == null || data.length == 0) {
      return "";
    }
    StringBuilder out = new StringBuilder((data.length * 8 + 4) / 5);
    int buffer = 0;
    int bitsLeft = 0;
    for (byte b : data) {
      buffer = (buffer << 8) | (b & 0xff);
      bitsLeft += 8;
      while (bitsLeft >= 5) {
        int index = (buffer >> (bitsLeft - 5)) & 0x1f;
        bitsLeft -= 5;
        out.append(ALPHABET[index]);
      }
    }
    if (bitsLeft > 0) {
      int index = (buffer << (5 - bitsLeft)) & 0x1f;
      out.append(ALPHABET[index]);
    }
    return out.toString();
  }

  static byte[] decode(String base32) {
    if (base32 == null || base32.isBlank()) {
      return new byte[0];
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    int buffer = 0;
    int bitsLeft = 0;
    for (int i = 0; i < base32.length(); i++) {
      char c = Character.toUpperCase(base32.charAt(i));
      if (c == '=' || c == ' ' || c == '\n' || c == '\r' || c == '\t' || c == '-') {
        continue;
      }
      if (c >= LOOKUP.length || LOOKUP[c] < 0) {
        continue;
      }
      buffer = (buffer << 5) | LOOKUP[c];
      bitsLeft += 5;
      if (bitsLeft >= 8) {
        int value = (buffer >> (bitsLeft - 8)) & 0xff;
        bitsLeft -= 8;
        out.write(value);
      }
    }
    return out.toByteArray();
  }
}
