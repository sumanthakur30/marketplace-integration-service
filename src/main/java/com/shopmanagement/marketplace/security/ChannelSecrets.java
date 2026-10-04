package com.shopmanagement.marketplace.security;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Splits secret keys out of channel config. Never logs values. */
public final class ChannelSecrets {

  public static final List<String> KNOWN_KEYS =
      List.of(
          "clientId",
          "clientSecret",
          "accessToken",
          "refreshToken",
          "webhookSecret",
          "apiKey",
          "password");

  private static final Set<String> KNOWN =
      Set.of(
          "clientid",
          "clientsecret",
          "accesstoken",
          "refreshtoken",
          "webhooksecret",
          "apikey",
          "password");

  private ChannelSecrets() {}

  public static boolean isSecretKey(String key) {
    if (key == null || key.isBlank()) {
      return false;
    }
    String lower = key.trim().toLowerCase(Locale.ROOT);
    if (KNOWN.contains(lower)) {
      return true;
    }
    return lower.contains("secret")
        || lower.contains("token")
        || lower.contains("password")
        || lower.contains("credential")
        || lower.contains("apikey");
  }

  public static String canonicalKey(String key) {
    if (key == null) {
      return null;
    }
    String lower = key.trim().toLowerCase(Locale.ROOT);
    for (String known : KNOWN_KEYS) {
      if (known.toLowerCase(Locale.ROOT).equals(lower)) {
        return known;
      }
    }
    return key.trim();
  }

  public static Map<String, Object> publicConfig(Map<String, Object> raw) {
    Map<String, Object> out = new LinkedHashMap<>();
    if (raw == null) {
      return out;
    }
    for (Map.Entry<String, Object> entry : raw.entrySet()) {
      if (entry.getKey() == null || isSecretKey(entry.getKey())) {
        continue;
      }
      out.put(entry.getKey(), entry.getValue());
    }
    return out;
  }

  public static Map<String, String> extractSecrets(Map<String, Object> raw) {
    Map<String, String> out = new LinkedHashMap<>();
    if (raw == null) {
      return out;
    }
    for (Map.Entry<String, Object> entry : raw.entrySet()) {
      if (!isSecretKey(entry.getKey())) {
        continue;
      }
      String value = asText(entry.getValue());
      if (value == null || value.isBlank()) {
        continue;
      }
      out.put(canonicalKey(entry.getKey()), value);
    }
    return out;
  }

  /**
   * Blank incoming values do not remove stored secrets. {@code clear} drops stored secrets first,
   * then applies only non-blank incoming values.
   */
  public static Map<String, String> merge(
      Map<String, String> existing, Map<String, ?> incoming, boolean clear) {
    Map<String, String> out = new LinkedHashMap<>();
    if (!clear && existing != null) {
      for (Map.Entry<String, String> entry : existing.entrySet()) {
        if (entry.getValue() != null && !entry.getValue().isBlank()) {
          out.put(canonicalKey(entry.getKey()), entry.getValue());
        }
      }
    }
    if (incoming == null) {
      return out;
    }
    for (Map.Entry<String, ?> entry : incoming.entrySet()) {
      if (!isSecretKey(entry.getKey())) {
        continue;
      }
      String value = asText(entry.getValue());
      if (value == null || value.isBlank()) {
        continue;
      }
      out.put(canonicalKey(entry.getKey()), value);
    }
    return out;
  }

  public static Set<String> fieldNames(Map<String, String> secrets) {
    Set<String> names = new LinkedHashSet<>();
    if (secrets == null) {
      return names;
    }
    for (Map.Entry<String, String> entry : secrets.entrySet()) {
      if (entry.getValue() != null && !entry.getValue().isBlank()) {
        names.add(canonicalKey(entry.getKey()));
      }
    }
    return names;
  }

  /** Removes secret keys from a response map, including one level of nested maps. */
  @SuppressWarnings("unchecked")
  public static Map<String, Object> scrub(Map<String, Object> raw) {
    Map<String, Object> out = new LinkedHashMap<>();
    if (raw == null) {
      return out;
    }
    for (Map.Entry<String, Object> entry : raw.entrySet()) {
      if (entry.getKey() == null || isSecretKey(entry.getKey())) {
        continue;
      }
      Object value = entry.getValue();
      if (value instanceof Map<?, ?> nested) {
        out.put(entry.getKey(), scrub((Map<String, Object>) nested));
      } else {
        out.put(entry.getKey(), value);
      }
    }
    return out;
  }

  private static String asText(Object value) {
    if (value == null) {
      return null;
    }
    return String.valueOf(value);
  }
}
