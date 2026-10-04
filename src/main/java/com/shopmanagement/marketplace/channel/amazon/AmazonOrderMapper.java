package com.shopmanagement.marketplace.channel.amazon;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopmanagement.marketplace.web.dto.OrderDtos;

/** Turns an SP-API orders page into ingest requests. Buyer name, email, and address are not copied. */
public final class AmazonOrderMapper {

  private static final ObjectMapper JSON = new ObjectMapper();

  private AmazonOrderMapper() {}

  public static String nextToken(String ordersJson) {
    JsonNode root = read(ordersJson);
    String token = text(root.path("payload").path("NextToken"));
    if (token == null) {
      token = text(root.path("NextToken"));
    }
    return token;
  }

  public static List<String> orderIds(String ordersJson) {
    JsonNode orders = orders(read(ordersJson));
    List<String> ids = new ArrayList<>();
    if (!orders.isArray()) {
      return ids;
    }
    for (JsonNode order : orders) {
      String orderId = text(order.path("AmazonOrderId"));
      if (orderId != null) {
        ids.add(orderId);
      }
    }
    return ids;
  }

  public static List<OrderDtos.IngestRequest> toIngest(String ordersJson, Map<String, String> itemsByOrderId) {
    JsonNode orders = orders(read(ordersJson));
    List<OrderDtos.IngestRequest> out = new ArrayList<>();
    if (!orders.isArray()) {
      return out;
    }
    for (JsonNode order : orders) {
      String orderId = text(order.path("AmazonOrderId"));
      if (orderId == null) {
        continue;
      }
      String itemsJson = itemsByOrderId == null ? null : itemsByOrderId.get(orderId);
      List<OrderDtos.Line> lines = lines(itemsJson);
      if (lines.isEmpty()) {
        continue;
      }
      BigDecimal total = money(order.path("OrderTotal").path("Amount"));
      if (total == null) {
        total = BigDecimal.ZERO;
        for (OrderDtos.Line line : lines) {
          if (line.unitPrice() != null && line.quantity() != null) {
            total = total.add(line.unitPrice().multiply(line.quantity()));
          }
        }
      }
      String currency = text(order.path("OrderTotal").path("CurrencyCode"));
      Map<String, Object> payload = new LinkedHashMap<>();
      String status = text(order.path("OrderStatus"));
      String purchased = text(order.path("PurchaseDate"));
      if (status != null) {
        payload.put("externalStatus", status);
      }
      if (purchased != null) {
        payload.put("purchaseDate", purchased);
      }
      out.add(
          new OrderDtos.IngestRequest(
              "AMAZON", orderId, currency == null ? "INR" : currency, total, null, lines, payload));
    }
    return out;
  }

  private static List<OrderDtos.Line> lines(String itemsJson) {
    List<OrderDtos.Line> lines = new ArrayList<>();
    if (itemsJson == null || itemsJson.isBlank()) {
      return lines;
    }
    JsonNode items = read(itemsJson).path("payload").path("OrderItems");
    if (!items.isArray()) {
      items = read(itemsJson).path("OrderItems");
    }
    if (!items.isArray()) {
      return lines;
    }
    for (JsonNode item : items) {
      int qty = item.path("QuantityOrdered").asInt(0);
      if (qty <= 0) {
        continue;
      }
      String asin = text(item.path("ASIN"));
      String sku = text(item.path("SellerSKU"));
      String listing = asin == null ? (sku == null ? "UNMAPPED" : sku) : asin;
      String channelSku = sku == null ? listing : sku;
      BigDecimal amount = money(item.path("ItemPrice").path("Amount"));
      BigDecimal quantity = BigDecimal.valueOf(qty);
      BigDecimal unit = null;
      if (amount != null) {
        unit = amount.divide(quantity, 2, RoundingMode.HALF_UP);
      }
      lines.add(new OrderDtos.Line(listing, channelSku, null, text(item.path("Title")), quantity, unit));
    }
    return lines;
  }

  private static JsonNode orders(JsonNode root) {
    JsonNode orders = root.path("payload").path("Orders");
    if (!orders.isArray()) {
      orders = root.path("Orders");
    }
    return orders;
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
