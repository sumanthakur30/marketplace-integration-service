package com.shopmanagement.marketplace.webhook;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.shopmanagement.marketplace.web.dto.OrderDtos;

/**
 * Maps Amazon order notifications (SP-API style or simplified pilot JSON) into ingest DTOs.
 *
 * <p>Pilot JSON:
 *
 * <pre>
 * { "amazonOrderId":"...", "orderStatus":"Unshipped",
 *   "items":[{"sellerSku":"X","quantityOrdered":1,"price":"10.00"}] }
 * </pre>
 */
@Component
public class AmazonOrderWebhookMapper {

  public boolean isOrderCreateOrUpdate(String eventType, Map<String, Object> payload) {
    String status = orderStatus(payload);
    if (status != null) {
      String s = status.toUpperCase();
      if (s.contains("CANCEL")) {
        return false;
      }
    }
    String type = eventType == null ? "" : eventType.toUpperCase();
    return type.contains("ORDER")
        || type.equals("ORDER_CHANGE")
        || type.equals("UNKNOWN")
        || payload.containsKey("amazonOrderId")
        || payload.containsKey("AmazonOrderId");
  }

  public boolean isOrderCancelled(String eventType, Map<String, Object> payload) {
    String status = orderStatus(payload);
    if (status != null && status.toUpperCase().contains("CANCEL")) {
      return true;
    }
    String type = eventType == null ? "" : eventType.toUpperCase();
    return type.contains("CANCEL");
  }

  public Optional<String> externalOrderId(Map<String, Object> payload) {
    if (payload == null) {
      return Optional.empty();
    }
    Object id = payload.get("amazonOrderId");
    if (id == null) {
      id = payload.get("AmazonOrderId");
    }
    if (id == null) {
      Object nested = payload.get("Payload");
      if (nested instanceof Map<?, ?> m) {
        Object ocn = m.get("OrderChangeNotification");
        if (ocn instanceof Map<?, ?> n) {
          id = n.get("AmazonOrderId");
        }
      }
    }
    if (id == null) {
      id = payload.get("id");
    }
    return id == null ? Optional.empty() : Optional.of(String.valueOf(id));
  }

  public OrderDtos.IngestRequest toIngestRequest(Map<String, Object> payload) {
    String externalId =
        externalOrderId(payload)
            .orElseThrow(() -> new IllegalArgumentException("Amazon payload missing order id"));
    List<OrderDtos.Line> items = new ArrayList<>();
    for (Map<String, Object> line : extractItems(payload)) {
      String sku = firstString(line, "sellerSku", "SellerSKU", "sku");
      BigDecimal qty = decimal(line.get("quantityOrdered"));
      if (qty == null) {
        qty = decimal(line.get("quantity"));
      }
      if (qty == null || qty.signum() <= 0) {
        continue;
      }
      items.add(
          new OrderDtos.Line(
              sku,
              sku,
              null,
              firstString(line, "title", "Title"),
              qty,
              decimal(firstObject(line, "price", "ItemPrice"))));
    }
    if (items.isEmpty()) {
      throw new IllegalArgumentException("Amazon order has no items");
    }
    return new OrderDtos.IngestRequest(
        "AMAZON",
        externalId,
        firstString(payload, "currency", "CurrencyCode") == null
            ? "INR"
            : firstString(payload, "currency", "CurrencyCode"),
        decimal(firstObject(payload, "orderTotal", "OrderTotal")),
        null,
        items,
        new LinkedHashMap<>(payload));
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> extractItems(Map<String, Object> payload) {
    Object items = payload.get("items");
    if (items instanceof List<?> list) {
      return (List<Map<String, Object>>) list;
    }
    items = payload.get("OrderItems");
    if (items instanceof List<?> list) {
      return (List<Map<String, Object>>) list;
    }
    return List.of();
  }

  private String orderStatus(Map<String, Object> payload) {
    if (payload == null) {
      return null;
    }
    Object status = payload.get("orderStatus");
    if (status == null) {
      status = payload.get("OrderStatus");
    }
    return status == null ? null : String.valueOf(status);
  }

  private static String firstString(Map<String, Object> map, String... keys) {
    Object v = firstObject(map, keys);
    return v == null ? null : String.valueOf(v);
  }

  private static Object firstObject(Map<String, Object> map, String... keys) {
    if (map == null) {
      return null;
    }
    for (String k : keys) {
      if (map.containsKey(k) && map.get(k) != null) {
        return map.get(k);
      }
    }
    return null;
  }

  private static BigDecimal decimal(Object raw) {
    if (raw == null) {
      return null;
    }
    if (raw instanceof BigDecimal bd) {
      return bd;
    }
    if (raw instanceof Number n) {
      return BigDecimal.valueOf(n.doubleValue());
    }
    if (raw instanceof Map<?, ?> m && m.get("Amount") != null) {
      return decimal(m.get("Amount"));
    }
    try {
      return new BigDecimal(String.valueOf(raw).trim());
    } catch (Exception ex) {
      return null;
    }
  }
}
