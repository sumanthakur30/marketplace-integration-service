package com.shopmanagement.marketplace.channel.flipkart;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopmanagement.marketplace.channel.amazon.AmazonCallException;

/** Flipkart seller token and one shipments page. Secrets are not logged. */
@Component
public class FlipkartClient {

  private final String baseUrl;
  private final RestClient.Builder restClientBuilder;
  private final ObjectMapper objectMapper;

  public FlipkartClient(
      @Value("${marketplace.flipkart.base-url:https://api.flipkart.net}") String baseUrl,
      RestClient.Builder restClientBuilder,
      ObjectMapper objectMapper) {
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    this.restClientBuilder = restClientBuilder;
    this.objectMapper = objectMapper;
  }

  public Token exchange(String clientId, String clientSecret) {
    String basic = Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
    JsonNode json =
        post(
            baseUrl + "/oauth-service/oauth/token",
            "grant_type=client_credentials",
            "Basic " + basic,
            MediaType.APPLICATION_FORM_URLENCODED);
    return token(json);
  }

  public Token refresh(String clientId, String clientSecret, String refreshToken) {
    String basic = Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
    String body = "grant_type=refresh_token&refresh_token=" + encode(refreshToken);
    JsonNode json =
        post(baseUrl + "/oauth-service/oauth/token", body, "Basic " + basic, MediaType.APPLICATION_FORM_URLENCODED);
    return token(json);
  }

  public String approvedShipments(String accessToken) {
    try {
      String raw =
          restClientBuilder
              .build()
              .post()
              .uri(baseUrl + "/sellers/v3/shipments/filter")
              .contentType(MediaType.APPLICATION_JSON)
              .header("Authorization", "Bearer " + accessToken)
              .body(Map.of("filter", Map.of("states", java.util.List.of("APPROVED"))))
              .retrieve()
              .body(String.class);
      return raw == null ? "{}" : raw;
    } catch (RestClientResponseException ex) {
      throw rejected(ex);
    } catch (AmazonCallException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new AmazonCallException(502, false, "Flipkart order pull could not be completed");
    }
  }

  private JsonNode post(String url, String body, String authorization, MediaType type) {
    try {
      String raw =
          restClientBuilder
              .build()
              .post()
              .uri(url)
              .contentType(type)
              .header("Authorization", authorization)
              .body(body)
              .retrieve()
              .body(String.class);
      return objectMapper.readTree(raw == null ? "{}" : raw);
    } catch (RestClientResponseException ex) {
      throw rejected(ex);
    } catch (AmazonCallException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new AmazonCallException(502, false, "Flipkart authorization could not be completed");
    }
  }

  private static Token token(JsonNode json) {
    String access = text(json.path("access_token"));
    if (access == null) {
      throw new AmazonCallException(502, false, "Flipkart did not return an access token");
    }
    return new Token(access, text(json.path("refresh_token")));
  }

  private static AmazonCallException rejected(RestClientResponseException ex) {
    int status = ex.getStatusCode().value();
    return new AmazonCallException(status, status == 400 || status == 401 || status == 403, "Flipkart returned " + status);
  }

  private static String encode(String value) {
    return java.net.URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
  }

  private static String text(JsonNode node) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return null;
    }
    String value = node.asText("").trim();
    return value.isEmpty() ? null : value;
  }

  public record Token(String accessToken, String refreshToken) {}
}
