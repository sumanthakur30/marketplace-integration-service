package com.shopmanagement.marketplace.stock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.shopmanagement.marketplace.config.MarketplaceProperties;

/**
 * Read + reserve/release against stock-service. Uses the same retail POS path ({@code
 * /stock/reserve-batch}) — never writes stock tables directly.
 */
@Component
public class StockAvailabilityClient {

  private static final Logger log = LoggerFactory.getLogger(StockAvailabilityClient.class);

  private final MarketplaceProperties properties;
  private final RestClient.Builder restClientBuilder;

  public StockAvailabilityClient(
      MarketplaceProperties properties, RestClient.Builder restClientBuilder) {
    this.properties = properties;
    this.restClientBuilder = restClientBuilder;
  }

  public Optional<Double> availableQuantity(String tenantId, String shopId, long productId) {
    if (!properties.getStock().isEnabled()) {
      return Optional.empty();
    }
    try {
      RestClient client = client();
      @SuppressWarnings("unchecked")
      Map<String, Object> body =
          client
              .get()
              .uri("/stock/check?productId={id}", productId)
              .headers(h -> applyTenant(h, tenantId, shopId))
              .retrieve()
              .body(Map.class);
      if (body == null) {
        return Optional.empty();
      }
      Object available = body.get("availableQuantity");
      if (available == null) {
        Object qty = body.get("quantity");
        Object reserved = body.get("reserved");
        if (qty instanceof Number q && reserved instanceof Number r) {
          return Optional.of(q.doubleValue() - r.doubleValue());
        }
      }
      if (available instanceof Number n) {
        return Optional.of(n.doubleValue());
      }
      return Optional.empty();
    } catch (Exception ex) {
      log.warn("stock availability lookup failed productId={}: {}", productId, ex.toString());
      if (properties.getStock().isFailOpen()) {
        return Optional.empty();
      }
      throw ex;
    }
  }

  public void reserveBatch(String tenantId, String shopId, Map<Long, Integer> productQty) {
    postBatch("/stock/reserve-batch", tenantId, shopId, productQty);
  }

  public void releaseBatch(String tenantId, String shopId, Map<Long, Integer> productQty) {
    postBatch("/stock/release-batch", tenantId, shopId, productQty);
  }

  private void postBatch(
      String path, String tenantId, String shopId, Map<Long, Integer> productQty) {
    if (!properties.getStock().isEnabled() || productQty == null || productQty.isEmpty()) {
      return;
    }
    List<Map<String, Object>> body = new ArrayList<>();
    for (Map.Entry<Long, Integer> e : productQty.entrySet()) {
      if (e.getKey() == null || e.getValue() == null || e.getValue() <= 0) {
        continue;
      }
      Map<String, Object> line = new LinkedHashMap<>();
      line.put("productId", e.getKey());
      line.put("quantity", e.getValue());
      line.put("reserved", 0);
      body.add(line);
    }
    if (body.isEmpty()) {
      return;
    }
    client()
        .post()
        .uri(path)
        .contentType(MediaType.APPLICATION_JSON)
        .headers(h -> applyTenant(h, tenantId, shopId))
        .body(body)
        .retrieve()
        .toBodilessEntity();
  }

  private RestClient client() {
    return restClientBuilder.baseUrl(properties.getStock().getBaseUrl()).build();
  }

  private void applyTenant(
      org.springframework.http.HttpHeaders headers, String tenantId, String shopId) {
    headers.add("X-Tenant-Id", tenantId);
    headers.add("X-Shop-Id", shopId == null ? "" : shopId);
    // Same pattern as order-service sales-return / lab clients — stock mutators require these.
    headers.add("X-Auth-Permissions", "MANAGE_STOCKS,MANAGE_ORDERS");
    String key = properties.getStock().getInternalApiKey();
    if (key != null && !key.isBlank()) {
      headers.add("X-Internal-Api-Key", key);
    }
  }
}
