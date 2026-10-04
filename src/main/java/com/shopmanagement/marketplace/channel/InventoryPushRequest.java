package com.shopmanagement.marketplace.channel;

import java.util.Map;

/** Context for channel inventory push. Secrets are overlaid from ciphertext for the adapter only. */
public record InventoryPushRequest(
    String listingId,
    String channelSku,
    double availableQty,
    Map<String, Object> channelConfig,
    Map<String, Object> mappingAttributes) {}
