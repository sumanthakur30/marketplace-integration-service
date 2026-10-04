package com.shopmanagement.marketplace.channel.amazon;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Short-lived state for the Amazon consent redirect. The URL never carries a client secret. */
public final class AmazonOauthState {

  public static final long TTL_SECONDS = 900;

  private AmazonOauthState() {}

  public static String sign(long channelId, String tenantId, long expiryEpoch, String key) {
    String body = channelId + "." + expiryEpoch;
    return body + "." + mac(body + "|" + tenantId, key);
  }

  public static void verify(String state, long channelId, String tenantId, String key, long nowEpoch) {
    if (state == null || state.isBlank()) {
      throw new IllegalArgumentException("Amazon authorization state is missing");
    }
    String[] parts = state.trim().split("\\.");
    if (parts.length != 3) {
      throw new IllegalArgumentException("Amazon authorization state is not valid");
    }
    long statedChannel;
    long expiry;
    try {
      statedChannel = Long.parseLong(parts[0]);
      expiry = Long.parseLong(parts[1]);
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException("Amazon authorization state is not valid");
    }
    if (statedChannel != channelId) {
      throw new IllegalArgumentException("Amazon authorization state does not match this channel");
    }
    if (nowEpoch > expiry) {
      throw new IllegalArgumentException("Amazon authorization state has expired");
    }
    String expected = mac(parts[0] + "." + parts[1] + "|" + tenantId, key);
    if (!MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.UTF_8), parts[2].getBytes(StandardCharsets.UTF_8))) {
      throw new IllegalArgumentException("Amazon authorization state is not valid");
    }
  }

  public static String consentUrl(String authorizeBase, String applicationId, String state, boolean draft) {
    String base = authorizeBase == null ? "" : authorizeBase.trim();
    if (base.endsWith("/")) {
      base = base.substring(0, base.length() - 1);
    }
    String url =
        base
            + "?application_id="
            + encode(applicationId)
            + "&state="
            + encode(state);
    if (draft) {
      url = url + "&version=beta";
    }
    String lower = url.toLowerCase(Locale.ROOT);
    if (lower.contains("client_secret") || lower.contains("refresh_token") || lower.contains("password")) {
      throw new IllegalStateException("Consent URL must not carry a secret");
    }
    return url;
  }

  private static String mac(String body, String key) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception ex) {
      throw new IllegalStateException("HMAC is unavailable");
    }
  }

  private static String encode(String value) {
    return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
  }
}
