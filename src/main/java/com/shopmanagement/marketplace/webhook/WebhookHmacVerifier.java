package com.shopmanagement.marketplace.webhook;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.shopmanagement.marketplace.config.MarketplaceProperties;

/** Opt-in HMAC verification for marketplace webhooks (Shopify base64 + generic hex). */
@Component
public class WebhookHmacVerifier {

  private final MarketplaceProperties properties;

  public WebhookHmacVerifier(MarketplaceProperties properties) {
    this.properties = properties;
  }

  public void verifyIfRequired(String channelCode, byte[] rawBody, String signatureHeader) {
    if (!properties.getInbound().isSigningEnabled()) {
      return;
    }
    String secret = resolveSecret(channelCode);
    if (secret == null || secret.isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.UNAUTHORIZED, "Inbound signing enabled but HMAC secret is empty");
    }
    if (signatureHeader == null || signatureHeader.isBlank()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing webhook signature header");
    }
    byte[] expected = hmacSha256(secret, rawBody == null ? new byte[0] : rawBody);
    String provided = signatureHeader.trim();
    boolean ok;
    if ("SHOPIFY".equalsIgnoreCase(channelCode)) {
      // X-Shopify-Hmac-Sha256 is Base64
      String expectedB64 = Base64.getEncoder().encodeToString(expected);
      ok = constantTimeEquals(expectedB64, provided);
    } else if (provided.startsWith("sha256=")) {
      String hex = provided.substring("sha256=".length()).trim();
      ok = constantTimeEquals(HexFormat.of().formatHex(expected), hex);
    } else {
      ok =
          constantTimeEquals(HexFormat.of().formatHex(expected), provided)
              || constantTimeEquals(Base64.getEncoder().encodeToString(expected), provided);
    }
    if (!ok) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid webhook HMAC signature");
    }
  }

  private String resolveSecret(String channelCode) {
    MarketplaceProperties.Inbound inbound = properties.getInbound();
    if ("SHOPIFY".equalsIgnoreCase(channelCode)
        && inbound.getShopifyHmacSecret() != null
        && !inbound.getShopifyHmacSecret().isBlank()) {
      return inbound.getShopifyHmacSecret();
    }
    if ("AMAZON".equalsIgnoreCase(channelCode)
        && inbound.getAmazonHmacSecret() != null
        && !inbound.getAmazonHmacSecret().isBlank()) {
      return inbound.getAmazonHmacSecret();
    }
    return inbound.getHmacSecret();
  }

  static byte[] hmacSha256(String secret, byte[] body) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return mac.doFinal(body);
    } catch (Exception ex) {
      throw new IllegalStateException("HMAC computation failed", ex);
    }
  }

  static boolean constantTimeEquals(String a, String b) {
    if (a == null || b == null) {
      return false;
    }
    return MessageDigest.isEqual(
        a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
  }
}
