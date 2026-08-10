package com.shopmanagement.marketplace.channel;

import java.util.Map;

/** Context for channel inventory push — credentials come from marketplace_channel.config_json. */
public record InventoryPushRequest(
    String listingId,
    String channelSku,
    double availableQty,
    Map<String, Object> channelConfig,
    Map<String, Object> mappingAttributes) {}
