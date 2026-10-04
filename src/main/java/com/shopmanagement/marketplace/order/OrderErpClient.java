package com.shopmanagement.marketplace.order;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.shopmanagement.marketplace.config.MarketplaceProperties;
import com.shopmanagement.marketplace.domain.MarketplaceOrderItem;

/**
 * Creates one retail order through the existing order-service sync path.
 * That path reserves stock once. This client does not call Amazon.
 */
@Component
public class OrderErpClient {

  private static final Logger log = LoggerFactory.getLogger(OrderErpClient.class);
  private static final ParameterizedTypeReference<Map<String, Object>> MAP =
      new ParameterizedTypeReference<>() {};

  private final MarketplaceProperties properties;
  private final RestClient.Builder restClientBuilder;

  public OrderErpClient(MarketplaceProperties properties, RestClient.Builder restClientBuilder) {
    this.properties = properties;
    this.restClientBuilder = restClientBuilder;
  }

  public Long createRetailOrder(
      String tenantId,
      String shopId,
      long channelId,
      String channelCode,
      String externalOrderId,
      long customerId,
      List<MarketplaceOrderItem> items) {
    String base = properties.getOrders().getBaseUrl();
    if (base == null || base.isBlank()) {
      throw new IllegalStateException("Sales bill service is not configured");
    }
    Map<String, Object> body = ErpOrderRequest.body(customerId, channelCode, externalOrderId, items);
    String key = ErpOrderRequest.idempotencyKey(channelId, externalOrderId);
    try {
      Map<String, Object> response =
          restClientBuilder
              .baseUrl(trimTrailingSlash(base))
              .build()
              .post()
              .uri("/orders/sync")
              .header("X-Tenant-Id", tenantId)
              .header("X-Shop-Id", shopId == null ? "" : shopId)
              .header("X-Auth-Permissions", "MANAGE_ORDERS")
              .header("X-Idempotency-Key", key)
              .header("X-Skip-Stock-Reserve", "true")
              .contentType(MediaType.APPLICATION_JSON)
              .body(body)
              .retrieve()
              .body(MAP);
      Long id = readId(response);
      if (id == null) {
        throw new IllegalStateException("Sales bill response had no id");
      }
      return id;
    } catch (RestClientResponseException ex) {
      log.warn("Marketplace bill bridge failed status={}", ex.getStatusCode().value());
      throw new IllegalStateException(safeMessage(ex));
    } catch (RestClientException ex) {
      log.warn("Marketplace bill bridge unavailable");
      throw new IllegalStateException("Sales bill service is unavailable");
    }
  }

  static Long readId(Map<String, Object> response) {
    if (response == null) {
      return null;
    }
    Object id = response.get("id");
    if (id instanceof Number number) {
      long value = number.longValue();
      return value > 0 ? value : null;
    }
    if (id == null) {
      return null;
    }
    try {
      long value = Long.parseLong(String.valueOf(id).trim());
      return value > 0 ? value : null;
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  static String safeMessage(RestClientResponseException ex) {
    String fallback = "Sales bill was not created (" + ex.getStatusCode().value() + ")";
    String raw = ex.getResponseBodyAsString();
    if (raw == null || raw.isBlank()) {
      return fallback;
    }
    String message = raw.trim();
    int marker = message.indexOf("\"message\"");
    if (marker >= 0) {
      int colon = message.indexOf(':', marker);
      int start = message.indexOf('"', colon + 1);
      int end = start >= 0 ? message.indexOf('"', start + 1) : -1;
      if (start >= 0 && end > start) {
        message = message.substring(start + 1, end);
      }
    }
    String lower = message.toLowerCase(Locale.ROOT);
    if (lower.contains("token")
        || lower.contains("secret")
        || lower.contains("password")
        || lower.contains("authorization")) {
      return fallback;
    }
    if (message.length() > 180) {
      message = message.substring(0, 180);
    }
    return message.isBlank() ? fallback : message;
  }

  private static String trimTrailingSlash(String base) {
    String trimmed = base.trim();
    while (trimmed.endsWith("/")) {
      trimmed = trimmed.substring(0, trimmed.length() - 1);
    }
    return trimmed;
  }
}
