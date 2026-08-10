package com.shopmanagement.marketplace.web.dto;

import java.math.BigDecimal;
import java.util.Map;

public final class MappingDtos {
  private MappingDtos() {}

  public record UpsertRequest(
      Long channelId,
      String shopId,
      Long productId,
      String skuCode,
      String channelListingId,
      String channelSku,
      Boolean syncInventory,
      BigDecimal allocationQty,
      Map<String, Object> attributes) {}
}
