package com.shopmanagement.marketplace.channel.connector;

import java.net.InetAddress;
import java.net.URI;
import java.util.Locale;

/** Allows a partner order URL and refuses loopback, link-local, and private addresses. */
public final class ConnectorUrl {

  private ConnectorUrl() {}

  public static URI requirePublicHttp(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException("Order URL is required");
    }
    URI uri;
    try {
      uri = URI.create(raw.trim());
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("Order URL is not valid");
    }
    String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
    if (!"https".equals(scheme) && !"http".equals(scheme)) {
      throw new IllegalArgumentException("Order URL must be http or https");
    }
    if (uri.getUserInfo() != null) {
      throw new IllegalArgumentException("Order URL must not include a username or password");
    }
    String host = uri.getHost();
    if (host == null || host.isBlank()) {
      throw new IllegalArgumentException("Order URL is not valid");
    }
    String lower = host.toLowerCase(Locale.ROOT);
    if ("localhost".equals(lower)
        || lower.endsWith(".localhost")
        || lower.endsWith(".local")
        || "metadata.google.internal".equals(lower)
        || "169.254.169.254".equals(lower)) {
      throw new IllegalArgumentException("Order URL must be a public partner address");
    }
    if (isPrivateLiteral(lower)) {
      throw new IllegalArgumentException("Order URL must be a public partner address");
    }
    return uri;
  }

  private static boolean isPrivateLiteral(String host) {
    try {
      InetAddress address = InetAddress.getByName(host);
      if (!host.chars().allMatch(ch -> Character.isDigit(ch) || ch == '.' || ch == ':')) {
        return false;
      }
      return address.isAnyLocalAddress()
          || address.isLoopbackAddress()
          || address.isLinkLocalAddress()
          || address.isSiteLocalAddress();
    } catch (Exception ex) {
      return false;
    }
  }
}
