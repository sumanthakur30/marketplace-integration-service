package com.shopmanagement.marketplace.channel.amazon;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopmanagement.marketplace.config.MarketplaceProperties;

/** Login with Amazon token exchange and signed Selling Partner order reads. */
@Component
public class AmazonSpApiClient {

  private static final DateTimeFormatter AMZ_DATE =
      DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

  private final MarketplaceProperties properties;
  private final RestClient.Builder restClientBuilder;
  private final ObjectMapper objectMapper;

  public AmazonSpApiClient(
      MarketplaceProperties properties, RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
    this.properties = properties;
    this.restClientBuilder = restClientBuilder;
    this.objectMapper = objectMapper;
  }

  public String exchangeRefreshToken(String code) {
    MarketplaceProperties.AmazonApi amazon = properties.getAmazon();
    String body =
        form(
            Map.of(
                "grant_type", "authorization_code",
                "code", code,
                "client_id", amazon.getLwaClientId(),
                "client_secret", amazon.getLwaClientSecret()));
    JsonNode json = postToken(amazon.getTokenUrl(), body);
    String refresh = text(json.path("refresh_token"));
    if (refresh == null) {
      throw new AmazonCallException(502, false, "Amazon did not return a refresh token");
    }
    return refresh;
  }

  public String accessToken(String refreshToken) {
    MarketplaceProperties.AmazonApi amazon = properties.getAmazon();
    String body =
        form(
            Map.of(
                "grant_type", "refresh_token",
                "refresh_token", refreshToken,
                "client_id", amazon.getLwaClientId(),
                "client_secret", amazon.getLwaClientSecret()));
    JsonNode json = postToken(amazon.getTokenUrl(), body);
    String access = text(json.path("access_token"));
    if (access == null) {
      throw new AmazonCallException(502, true, "Amazon did not return an access token");
    }
    return access;
  }

  public String getOrders(String accessToken, Map<String, String> query) {
    return signedGet("/orders/v0/orders", query, accessToken);
  }

  public String getOrderItems(String accessToken, String amazonOrderId) {
    return signedGet("/orders/v0/orders/" + encode(amazonOrderId) + "/orderItems", Map.of(), accessToken);
  }

  private JsonNode postToken(String url, String body) {
    try {
      String raw =
          restClientBuilder
              .build()
              .post()
              .uri(URI.create(url))
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .body(body)
              .retrieve()
              .body(String.class);
      return objectMapper.readTree(raw == null ? "{}" : raw);
    } catch (RestClientResponseException ex) {
      throw rejected(ex);
    } catch (AmazonCallException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new AmazonCallException(502, false, "Amazon authorization could not be completed");
    }
  }

  private String signedGet(String path, Map<String, String> query, String accessToken) {
    MarketplaceProperties.AmazonApi amazon = properties.getAmazon();
    URI endpoint = URI.create(amazon.getEndpoint().trim());
    String host = endpoint.getHost();
    String queryString = canonicalQuery(query);
    String amzDate = AMZ_DATE.format(Instant.now());
    Map<String, String> signed = new LinkedHashMap<>();
    signed.put("host", host);
    signed.put("x-amz-access-token", accessToken);
    signed.put("x-amz-date", amzDate);
    String authorization =
        AwsSigV4.authorization(
            "GET",
            path,
            queryString,
            signed,
            amazon.getRegion(),
            "execute-api",
            amazon.getAwsAccessKeyId().trim(),
            amazon.getAwsSecretAccessKey().trim(),
            amzDate);
    String url = endpoint.getScheme() + "://" + host + path + (queryString.isEmpty() ? "" : "?" + queryString);
    try {
      return restClientBuilder
          .build()
          .get()
          .uri(URI.create(url))
          .header("host", host)
          .header("x-amz-access-token", accessToken)
          .header("x-amz-date", amzDate)
          .header("Authorization", authorization)
          .header("user-agent", "SugamFlow/1.0 (Language=Java)")
          .retrieve()
          .body(String.class);
    } catch (RestClientResponseException ex) {
      throw rejected(ex);
    } catch (AmazonCallException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new AmazonCallException(502, false, "Amazon order pull could not be completed");
    }
  }

  static String canonicalQuery(Map<String, String> query) {
    if (query == null || query.isEmpty()) {
      return "";
    }
    TreeMap<String, String> sorted = new TreeMap<>();
    for (Map.Entry<String, String> entry : query.entrySet()) {
      if (entry.getKey() == null || entry.getValue() == null || entry.getValue().isBlank()) {
        continue;
      }
      sorted.put(encode(entry.getKey()), encode(entry.getValue()));
    }
    StringBuilder out = new StringBuilder();
    for (Map.Entry<String, String> entry : sorted.entrySet()) {
      if (out.length() > 0) {
        out.append('&');
      }
      out.append(entry.getKey()).append('=').append(entry.getValue());
    }
    return out.toString();
  }

  private static String form(Map<String, String> fields) {
    return canonicalQuery(fields);
  }

  private static AmazonCallException rejected(RestClientResponseException ex) {
    int status = ex.getStatusCode().value();
    boolean token = status == 400 || status == 401 || status == 403;
    return new AmazonCallException(status, token, "Amazon returned " + status);
  }

  private static String text(JsonNode node) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return null;
    }
    String value = node.asText("").trim();
    return value.isEmpty() ? null : value;
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }
}
