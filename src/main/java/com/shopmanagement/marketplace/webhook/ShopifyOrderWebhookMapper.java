package com.shopmanagement.marketplace.webhook;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.shopmanagement.marketplace.web.dto.OrderDtos;

/** Maps Shopify Admin webhook JSON (orders/*) into marketplace ingest DTOs. */
@Component
public class ShopifyOrderWebhookMapper {

  public boolean isOrderCreateOrUpdate(String topic) {
    if (topic == null) {
      return false;
    }
    String t = topic.trim().toLowerCase();
    return t.equals("orders/create")
        || t.equals("orders/updated")
        || t.equals("orders/edited")
        || t.equals("orders/paid");
  }

  public boolean isOrderCancelled(String topic) {
    return topic != null && topic.trim().equalsIgnoreCase("orders/cancelled");
  }

  public Optional<String> externalOrderId(Map<String, Object> payload) {
    if (payload == null) {
      return Optional.empty();
    }
    Object id = payload.get("id");
    if (id == null) {
      return Optional.empty();
    }
    return Optional.of(String.valueOf(id));
  }

  public OrderDtos.IngestRequest toIngestRequest(Map<String, Object> payload) {
    String externalId =
        externalOrderId(payload)
            .orElseThrow(() -> new IllegalArgumentException("Shopify order payload missing id"));
    String currency = stringVal(payload.get("currency"), "INR");
    BigDecimal total = decimalVal(payload.get("total_price"));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> lines =
        payload.get("line_items") instanceof List<?> list
            ? (List<Map<String, Object>>) list
            : List.of();
    List<OrderDtos.Line> items = new ArrayList<>();
    for (Map<String, Object> line : lines) {
      if (line == null) {
        continue;
      }
      String sku = stringVal(line.get("sku"), null);
      String listing =
          firstNonBlank(
              stringVal(line.get("variant_id"), null), stringVal(line.get("product_id"), null));
      BigDecimal qty = decimalVal(line.get("quantity"));
      if (qty == null || qty.signum() <= 0) {
        continue;
      }
      items.add(
          new OrderDtos.Line(
              listing,
              sku,
              null,
              stringVal(line.get("title"), null),
              qty,
              decimalVal(line.get("price"))));
    }
    if (items.isEmpty()) {
      throw new IllegalArgumentException("Shopify order has no line_items");
    }
    return new OrderDtos.IngestRequest(
        "SHOPIFY", externalId, currency, total, true, items, new java.util.LinkedHashMap<>(payload));
  }

  private static String firstNonBlank(String a, String b) {
    if (a != null && !a.isBlank()) {
      return a;
    }
    return b;
  }

  private static String stringVal(Object raw, String fallback) {
    if (raw == null) {
      return fallback;
    }
    String s = String.valueOf(raw).trim();
    return s.isEmpty() ? fallback : s;
  }

  private static BigDecimal decimalVal(Object raw) {
    if (raw == null) {
      return null;
    }
    if (raw instanceof BigDecimal bd) {
      return bd;
    }
    if (raw instanceof Number n) {
      return BigDecimal.valueOf(n.doubleValue());
    }
    try {
      return new BigDecimal(String.valueOf(raw).trim());
    } catch (Exception ex) {
      return null;
    }
  }
}
