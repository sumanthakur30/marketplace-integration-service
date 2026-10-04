package com.shopmanagement.marketplace.channel.flipkart;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopmanagement.marketplace.web.dto.OrderDtos;

/** Maps a Flipkart shipments page. Buyer name, phone, and address are not copied. */
public final class FlipkartOrderMapper {

  private static final ObjectMapper JSON = new ObjectMapper();

  private FlipkartOrderMapper() {}

  public static boolean hasMore(String json) {
    JsonNode root = read(json);
    if (root.path("hasMore").asBoolean(false)) {
      return true;
    }
    String next = text(root.path("nextPageUrl"));
    return next != null;
  }

  public static List<OrderDtos.IngestRequest> toIngest(String json) {
    JsonNode shipments = read(json).path("shipments");
    if (!shipments.isArray()) {
      shipments = read(json).path("payload").path("shipments");
    }
    Map<String, List<OrderDtos.Line>> linesByOrder = new LinkedHashMap<>();
    Map<String, BigDecimal> totals = new LinkedHashMap<>();
    if (shipments.isArray()) {
      for (JsonNode shipment : shipments) {
        String orderId = text(shipment.path("orderId"));
        if (orderId == null) {
          continue;
        }
        JsonNode items = shipment.path("orderItems");
        if (!items.isArray()) {
          continue;
        }
        for (JsonNode item : items) {
          int qty = item.path("quantity").asInt(0);
          if (qty <= 0) {
            continue;
          }
          String sku = text(item.path("sku"));
          String fsn = text(item.path("fsn"));
          String listing = fsn == null ? (sku == null ? "UNMAPPED" : sku) : fsn;
          String channelSku = sku == null ? listing : sku;
          BigDecimal price = money(item.path("priceComponents").path("sellingPrice"));
          if (price == null) {
            price = money(item.path("price"));
          }
          linesByOrder.computeIfAbsent(orderId, key -> new ArrayList<>())
              .add(new OrderDtos.Line(listing, channelSku, null, text(item.path("title")), BigDecimal.valueOf(qty), price));
          if (price != null) {
            totals.merge(orderId, price.multiply(BigDecimal.valueOf(qty)), BigDecimal::add);
          }
        }
      }
    }
    List<OrderDtos.IngestRequest> out = new ArrayList<>();
    for (Map.Entry<String, List<OrderDtos.Line>> entry : linesByOrder.entrySet()) {
      if (entry.getValue().isEmpty()) {
        continue;
      }
      out.add(
          new OrderDtos.IngestRequest(
              "FLIPKART",
              entry.getKey(),
              "INR",
              totals.getOrDefault(entry.getKey(), BigDecimal.ZERO),
              null,
              entry.getValue(),
              Map.of("externalStatus", "APPROVED")));
    }
    return out;
  }

  private static JsonNode read(String json) {
    try {
      return JSON.readTree(json == null ? "{}" : json);
    } catch (Exception ex) {
      return JSON.createObjectNode();
    }
  }

  private static String text(JsonNode node) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return null;
    }
    String value = node.asText("").trim();
    return value.isEmpty() ? null : value;
  }

  private static BigDecimal money(JsonNode node) {
    String value = text(node);
    if (value == null) {
      return null;
    }
    try {
      return new BigDecimal(value);
    } catch (NumberFormatException ex) {
      return null;
    }
  }
}
