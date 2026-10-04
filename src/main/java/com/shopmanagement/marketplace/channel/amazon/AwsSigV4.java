package com.shopmanagement.marketplace.channel.amazon;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** AWS Signature Version 4 for Selling Partner API GET calls. */
public final class AwsSigV4 {

  private AwsSigV4() {}

  public static String emptyPayloadHash() {
    return sha256Hex("");
  }

  public static String authorization(
      String method,
      String canonicalUri,
      String canonicalQuery,
      Map<String, String> headers,
      String region,
      String service,
      String accessKey,
      String secret,
      String amzDate) {
    String dateStamp = amzDate.substring(0, 8);
    String canonical = canonicalRequest(method, canonicalUri, canonicalQuery, headers);
    String signed = signedHeaders(headers);
    String scope = dateStamp + "/" + region + "/" + service + "/aws4_request";
    String stringToSign = "AWS4-HMAC-SHA256\n" + amzDate + "\n" + scope + "\n" + sha256Hex(canonical);
    byte[] signingKey =
        hmac(hmac(hmac(hmac(("AWS4" + secret).getBytes(StandardCharsets.UTF_8), dateStamp), region), service), "aws4_request");
    String signature = HexFormat.of().formatHex(hmac(signingKey, stringToSign));
    return "AWS4-HMAC-SHA256 Credential="
        + accessKey
        + "/"
        + scope
        + ", SignedHeaders="
        + signed
        + ", Signature="
        + signature;
  }

  static String canonicalRequest(String method, String canonicalUri, String canonicalQuery, Map<String, String> headers) {
    List<String> names = new ArrayList<>(headers.keySet());
    names.sort(String.CASE_INSENSITIVE_ORDER);
    StringBuilder canonicalHeaders = new StringBuilder();
    for (String name : names) {
      canonicalHeaders.append(name.toLowerCase(Locale.ROOT)).append(':').append(headers.get(name).trim()).append('\n');
    }
    return method
        + "\n"
        + canonicalUri
        + "\n"
        + (canonicalQuery == null ? "" : canonicalQuery)
        + "\n"
        + canonicalHeaders
        + "\n"
        + signedHeaders(headers)
        + "\n"
        + emptyPayloadHash();
  }

  private static String signedHeaders(Map<String, String> headers) {
    List<String> names = new ArrayList<>(headers.keySet());
    names.sort(String.CASE_INSENSITIVE_ORDER);
    StringBuilder signed = new StringBuilder();
    for (String name : names) {
      if (signed.length() > 0) {
        signed.append(';');
      }
      signed.append(name.toLowerCase(Locale.ROOT));
    }
    return signed.toString();
  }

  static String sha256Hex(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception ex) {
      throw new IllegalStateException("SHA-256 is unavailable");
    }
  }

  static String signingKeyHex(String secret, String dateStamp, String region, String service) {
    byte[] signingKey =
        hmac(hmac(hmac(hmac(("AWS4" + secret).getBytes(StandardCharsets.UTF_8), dateStamp), region), service), "aws4_request");
    return HexFormat.of().formatHex(signingKey);
  }

  private static byte[] hmac(byte[] key, String data) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));
      return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    } catch (Exception ex) {
      throw new IllegalStateException("HMAC is unavailable");
    }
  }
}
