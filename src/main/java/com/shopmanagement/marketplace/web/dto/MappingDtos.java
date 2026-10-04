package com.shopmanagement.marketplace.web.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class MappingDtos {
  private MappingDtos() {}

  public record SuggestRequest(String channelSku, List<SkuMatchProduct> products) {}

  public record SkuMatchProduct(Long productId, String code, String barcode) {}

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
