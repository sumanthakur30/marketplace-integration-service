package com.shopmanagement.marketplace.channel.connector;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopmanagement.marketplace.web.dto.OrderDtos;

/** Maps a partner JSON list using configured field names. There is no script. */
public final class ConnectorOrderMapper {

  private static final ObjectMapper JSON = new ObjectMapper();

  private ConnectorOrderMapper() {}

  public static List<OrderDtos.IngestRequest> toIngest(String json, Map<String, String> fields, String channelCode) {
    JsonNode root = read(json);
    String ordersPath = field(fields, "ordersPath", "");
    JsonNode orders = ordersPath.isEmpty() ? root : at(root, ordersPath);
    if (orders.isObject()) {
      orders = orders.path("orders");
    }
    List<OrderDtos.IngestRequest> out = new ArrayList<>();
    if (!orders.isArray()) {
      return out;
    }
    String code = channelCode == null || channelCode.isBlank() ? "OTHER" : channelCode.trim().toUpperCase();
    String idField = field(fields, "orderIdField", "id");
    String skuField = field(fields, "skuField", "sku");
    String qtyField = field(fields, "quantityField", "quantity");
    String priceField = field(fields, "priceField", "price");
    String listingField = field(fields, "listingField", "listingId");
    int kept = 0;
    for (JsonNode order : orders) {
      if (kept >= 20) {
        break;
      }
      String orderId = text(at(order, idField));
      if (orderId == null) {
        continue;
      }
      JsonNode linesNode = order.path("items");
      if (!linesNode.isArray()) {
        linesNode = order.path("lines");
      }
      List<OrderDtos.Line> lines = new ArrayList<>();
      if (linesNode.isArray()) {
        for (JsonNode line : linesNode) {
          addLine(lines, line, skuField, qtyField, priceField, listingField);
        }
      } else {
        addLine(lines, order, skuField, qtyField, priceField, listingField);
      }
      if (lines.isEmpty()) {
        continue;
      }
      BigDecimal total = BigDecimal.ZERO;
      for (OrderDtos.Line line : lines) {
        if (line.unitPrice() != null && line.quantity() != null) {
          total = total.add(line.unitPrice().multiply(line.quantity()));
        }
      }
      out.add(new OrderDtos.IngestRequest(code, orderId, "INR", total, null, lines, Map.of()));
      kept++;
    }
    return out;
  }

  private static void addLine(
      List<OrderDtos.Line> lines,
      JsonNode node,
      String skuField,
      String qtyField,
      String priceField,
      String listingField) {
    int qty = at(node, qtyField).asInt(0);
    if (qty <= 0) {
      return;
    }
    String sku = text(at(node, skuField));
    String listing = text(at(node, listingField));
    if (listing == null) {
      listing = sku == null ? "UNMAPPED" : sku;
    }
    if (sku == null) {
      sku = listing;
    }
    lines.add(new OrderDtos.Line(listing, sku, null, text(at(node, "title")), BigDecimal.valueOf(qty), money(at(node, priceField))));
  }

  private static JsonNode at(JsonNode node, String path) {
    JsonNode current = node;
    for (String part : path.split("\\.")) {
      if (part.isBlank() || current == null) {
        break;
      }
      current = current.path(part);
    }
    return current == null ? com.fasterxml.jackson.databind.node.MissingNode.getInstance() : current;
  }

  private static String field(Map<String, String> fields, String key, String fallback) {
    if (fields == null || fields.get(key) == null || fields.get(key).isBlank()) {
      return fallback;
    }
    return fields.get(key).trim();
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
    if (node == null || node.isMissingNode() || node.isNull()) {
      return null;
    }
    if (node.isNumber()) {
      return node.decimalValue();
    }
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
