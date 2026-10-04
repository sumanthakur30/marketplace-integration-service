package com.shopmanagement.marketplace.security;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Non-secret channel settings. Missing sync flags stay off. Interval 0 means manual. */
public final class ChannelSettings {

  public static final List<String> FLAG_KEYS =
      List.of(
          "inventorySyncEnabled",
          "orderSyncEnabled",
          "productSyncEnabled",
          "priceSyncEnabled",
          "stockSyncEnabled",
          "customerSyncEnabled",
          "webhookEnabled",
          "autoInvoiceEnabled",
          "autoReserveStockEnabled",
          "autoConfirmOrderEnabled",
          "autoPrintEnabled",
          "notificationEnabled");

  private static final Set<Integer> INTERVALS = Set.of(0, 1, 5, 15, 30, 60);

  private static final List<String> TEXT_KEYS =
      List.of(
          "warehouseId",
          "defaultCustomerId",
          "defaultPriceList",
          "defaultTaxConfiguration",
          "defaultPaymentMode",
          "defaultShippingConfiguration",
          "orderPrefix",
          "invoicePrefix",
          "returnPolicy",
          "marketplaceId",
          "region",
          "apiEndpoint",
          "inventoryEndpoint",
          "authHeader",
          "connectorOrdersPath",
          "connectorOrderIdField",
          "connectorSkuField",
          "connectorQuantityField",
          "connectorPriceField",
          "connectorListingField",
          "shopDomain",
          "locationId",
          "sellerId");

  private ChannelSettings() {}

  public static Map<String, Object> defaults() {
    Map<String, Object> out = new LinkedHashMap<>();
    for (String flag : FLAG_KEYS) {
      out.put(flag, Boolean.FALSE);
    }
    out.put("syncIntervalMinutes", 0);
    out.put("inventoryBufferQty", 0);
    return out;
  }

  public static Map<String, Object> normalize(Map<String, Object> existingPublic, Map<String, Object> incoming) {
    Map<String, Object> merged = new LinkedHashMap<>();
    if (existingPublic != null) {
      merged.putAll(ChannelSecrets.publicConfig(existingPublic));
    }
    Map<String, Object> source = incoming == null ? Map.of() : ChannelSecrets.publicConfig(incoming);
    for (Map.Entry<String, Object> entry : source.entrySet()) {
      if (entry.getValue() == null) {
        merged.remove(entry.getKey());
      } else {
        merged.put(entry.getKey(), entry.getValue());
      }
    }
    for (String flag : FLAG_KEYS) {
      merged.put(flag, asBoolean(merged.get(flag)));
    }
    merged.put("syncIntervalMinutes", interval(merged.get("syncIntervalMinutes")));
    merged.put("inventoryBufferQty", buffer(merged.get("inventoryBufferQty")));
    for (String key : TEXT_KEYS) {
      if (!merged.containsKey(key)) {
        continue;
      }
      String text = String.valueOf(merged.get(key)).trim();
      if (text.isEmpty() || "null".equals(text)) {
        merged.remove(key);
        continue;
      }
      int max = "returnPolicy".equals(key) || "apiEndpoint".equals(key) ? 500 : 128;
      if (text.length() > max) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " is too long");
      }
      merged.put(key, text);
    }
    return merged;
  }

  private static boolean asBoolean(Object value) {
    if (value instanceof Boolean b) {
      return b;
    }
    if (value == null) {
      return false;
    }
    String text = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
    return "true".equals(text) || "1".equals(text) || "yes".equals(text);
  }

  private static int interval(Object value) {
    int minutes = asInt(value, 0);
    if (!INTERVALS.contains(minutes)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "syncIntervalMinutes must be 0, 1, 5, 15, 30, or 60");
    }
    return minutes;
  }

  private static int buffer(Object value) {
    int qty = asInt(value, 0);
    if (qty < 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "inventoryBufferQty cannot be negative");
    }
    return qty;
  }

  private static int asInt(Object value, int fallback) {
    if (value == null) {
      return fallback;
    }
    if (value instanceof Number number) {
      return number.intValue();
    }
    String text = String.valueOf(value).trim();
    if (text.isEmpty()) {
      return fallback;
    }
    try {
      return Integer.parseInt(text);
    } catch (NumberFormatException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expected a whole number");
    }
  }
}
