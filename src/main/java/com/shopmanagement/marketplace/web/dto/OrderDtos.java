package com.shopmanagement.marketplace.web.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class OrderDtos {
  private OrderDtos() {}

  public record IngestRequest(
      String channelCode,
      String externalOrderId,
      String currency,
      BigDecimal totalAmount,
      Boolean reserveStock,
      List<Line> items,
      Map<String, Object> payload) {}

  public record Line(
      String channelListingId,
      String channelSku,
      Long productId,
      String title,
      BigDecimal quantity,
      BigDecimal unitPrice) {}
}
