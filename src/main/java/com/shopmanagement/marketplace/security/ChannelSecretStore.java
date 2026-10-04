package com.shopmanagement.marketplace.security;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Encrypts the secret map as one ciphertext blob. Callers never log the plaintext. */
@Component
public class ChannelSecretStore {

  private static final Logger log = LoggerFactory.getLogger(ChannelSecretStore.class);
  private static final TypeReference<LinkedHashMap<String, String>> MAP_TYPE = new TypeReference<>() {};

  private final ChannelCredentialCipher cipher;
  private final ObjectMapper objectMapper;

  public ChannelSecretStore(ChannelCredentialCipher cipher, ObjectMapper objectMapper) {
    this.cipher = cipher;
    this.objectMapper = objectMapper;
  }

  public SecretRead read(String ciphertext) {
    if (ciphertext == null || ciphertext.isBlank()) {
      return SecretRead.empty();
    }
    try {
      String json = cipher.decrypt(ciphertext);
      if (json == null || json.isBlank()) {
        return SecretRead.empty();
      }
      Map<String, String> parsed = objectMapper.readValue(json, MAP_TYPE);
      return new SecretRead(false, parsed == null ? new LinkedHashMap<>() : new LinkedHashMap<>(parsed));
    } catch (RuntimeException ex) {
      log.warn("Channel credential decrypt failed");
      return SecretRead.unreadable();
    } catch (Exception ex) {
      log.warn("Channel credential payload unreadable");
      return SecretRead.unreadable();
    }
  }

  public String write(Map<String, String> secrets) {
    if (secrets == null || secrets.isEmpty()) {
      return null;
    }
    try {
      return cipher.encrypt(objectMapper.writeValueAsString(secrets));
    } catch (Exception ex) {
      throw new IllegalStateException("Unable to store channel credentials");
    }
  }

  /** Adapter-only view: public config plus decrypted secrets. Do not return this from HTTP. */
  public Map<String, Object> adapterConfig(Map<String, Object> publicOrLegacyConfig, String ciphertext) {
    Map<String, Object> out = new LinkedHashMap<>();
    if (publicOrLegacyConfig != null) {
      out.putAll(publicOrLegacyConfig);
    }
    SecretRead current = read(ciphertext);
    if (!current.unreadable) {
      current.secrets.forEach(out::put);
    }
    return Collections.unmodifiableMap(out);
  }

  public static final class SecretRead {
    public final boolean unreadable;
    public final Map<String, String> secrets;

    SecretRead(boolean unreadable, Map<String, String> secrets) {
      this.unreadable = unreadable;
      this.secrets = secrets;
    }

    static SecretRead empty() {
      return new SecretRead(false, new LinkedHashMap<>());
    }

    static SecretRead unreadable() {
      return new SecretRead(true, new LinkedHashMap<>());
    }
  }
}
