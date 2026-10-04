package com.shopmanagement.marketplace.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AES-GCM for channel credentials. Ciphertext is stored in {@code credentials_ciphertext}.
 * API responses never return this value.
 */
@Component
public class ChannelCredentialCipher {

  static final String PREFIX = "ENC1:";
  private static final int GCM_TAG_BITS = 128;
  private static final int IV_BYTES = 12;
  private static final String FALLBACK = "change-me-marketplace-credentials-key";

  private final byte[] keyBytes;

  public ChannelCredentialCipher(
      @Value("${marketplace.credentials.key:change-me-marketplace-credentials-key}") String key) {
    this.keyBytes = normalizeKey(key);
  }

  public String encrypt(String plaintext) {
    if (plaintext == null || plaintext.isBlank()) {
      return null;
    }
    try {
      byte[] iv = new byte[IV_BYTES];
      new SecureRandom().nextBytes(iv);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBytes, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
      byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
      byte[] payload = new byte[iv.length + encrypted.length];
      System.arraycopy(iv, 0, payload, 0, iv.length);
      System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
      return PREFIX + Base64.getEncoder().encodeToString(payload);
    } catch (GeneralSecurityException ex) {
      throw new IllegalStateException("Unable to encrypt channel credential");
    }
  }

  public String decrypt(String ciphertext) {
    if (ciphertext == null || ciphertext.isBlank()) {
      return null;
    }
    if (!ciphertext.startsWith(PREFIX)) {
      return ciphertext;
    }
    try {
      byte[] payload = Base64.getDecoder().decode(ciphertext.substring(PREFIX.length()));
      if (payload.length <= IV_BYTES) {
        throw new IllegalArgumentException("short");
      }
      byte[] iv = new byte[IV_BYTES];
      byte[] encrypted = new byte[payload.length - IV_BYTES];
      System.arraycopy(payload, 0, iv, 0, IV_BYTES);
      System.arraycopy(payload, IV_BYTES, encrypted, 0, encrypted.length);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
      return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    } catch (GeneralSecurityException | IllegalArgumentException ex) {
      throw new IllegalStateException("Unable to decrypt channel credential");
    }
  }

  static byte[] normalizeKey(String key) {
    String normalized = key == null ? "" : key.trim();
    if (normalized.length() < 16) {
      normalized = (normalized + FALLBACK).substring(0, 32);
    } else if (normalized.length() > 32) {
      normalized = normalized.substring(0, 32);
    } else {
      normalized = String.format("%-32s", normalized).replace(' ', '0');
    }
    return normalized.getBytes(StandardCharsets.UTF_8);
  }
}
