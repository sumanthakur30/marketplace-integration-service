package com.shopmanagement.marketplace.order;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.shopmanagement.marketplace.domain.MarketplaceOrderItem;

/**
 * Body for one existing retail order. Does not create a customer or a product.
 * Payment stays unpaid so a later settlement is not recorded as a customer receipt.
 */
public final class ErpOrderRequest {

  public static final String PAYMENT_METHOD = "PAY_LATER";
  public static final String PAYMENT_STATUS = "UNPAID";

  private ErpOrderRequest() {}

  public static boolean orderSyncEnabled(Map<String, Object> config) {
    if (config == null) {
      return false;
    }
    Object value = config.get("orderSyncEnabled");
    if (value instanceof Boolean enabled) {
      return enabled;
    }
    return value != null && "true".equalsIgnoreCase(String.valueOf(value).trim());
  }

  public static Long customerId(Object raw) {
    if (raw == null) {
      return null;
    }
    String text = String.valueOf(raw).trim();
    if (text.isEmpty()) {
      return null;
    }
    try {
      long id = Long.parseLong(text);
      return id > 0 ? id : null;
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  public static String idempotencyKey(long channelId, String externalOrderId) {
    String external = externalOrderId == null ? "" : externalOrderId.trim();
    String key = "mp-" + channelId + "-" + external;
    return key.length() <= 128 ? key : key.substring(0, 128);
  }

  public static Map<String, Object> body(
      long customerId, String channelCode, String externalOrderId, List<MarketplaceOrderItem> items) {
    String channel = channelCode == null ? "OTHER" : channelCode.trim().toUpperCase();
    if (channel.length() > 30) {
      channel = channel.substring(0, 30);
    }
    String external = externalOrderId == null ? "" : externalOrderId.trim();
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("customerId", customerId);
    body.put("orderChannel", channel);
    body.put("paymentMethod", PAYMENT_METHOD);
    body.put("paymentStatus", PAYMENT_STATUS);
    body.put("paidAmount", 0d);
    body.put("includeGst", true);
    body.put("notes", note(channel, external));
    List<Map<String, Object>> lines = new ArrayList<>();
    if (items != null) {
      for (MarketplaceOrderItem item : items) {
        lines.add(line(item));
      }
    }
    body.put("items", lines);
    return body;
  }

  private static Map<String, Object> line(MarketplaceOrderItem item) {
    if (item.getProductId() == null) {
      throw new IllegalArgumentException("Line has no product");
    }
    Map<String, Object> line = new LinkedHashMap<>();
    line.put("productId", item.getProductId());
    line.put("quantity", wholeQuantity(item.getQuantity()));
    line.put("price", item.getUnitPrice() == null ? 0d : item.getUnitPrice().doubleValue());
    if (item.getTitle() != null && !item.getTitle().isBlank()) {
      line.put("productName", item.getTitle().trim());
    }
    return line;
  }

  static int wholeQuantity(BigDecimal quantity) {
    if (quantity == null || quantity.signum() <= 0) {
      throw new IllegalArgumentException("Line quantity must be > 0");
    }
    try {
      return quantity.setScale(0, RoundingMode.UP).intValueExact();
    } catch (ArithmeticException ex) {
      throw new IllegalArgumentException("Line quantity is too large for a sales bill");
    }
  }

  private static String note(String channel, String externalOrderId) {
    String note = "Marketplace " + channel + " " + externalOrderId;
    return note.length() <= 500 ? note : note.substring(0, 500);
  }
}
