package com.shopmanagement.marketplace.channel;

import java.util.Map;

/** Channel adapter SPI — real Amazon/Flipkart/Shopify SDKs plug in without touching ERP. */
public interface MarketplaceChannelAdapter {

  MarketplaceChannelCode code();

  boolean isConfigured();

  /** Push sellable qty for a listing. Stub returns accepted=false until credentials wired. */
  Map<String, Object> pushInventory(String listingId, String channelSku, double availableQty);

  default Map<String, Object> pushInventory(InventoryPushRequest request) {
    return pushInventory(request.listingId(), request.channelSku(), request.availableQty());
  }

  /** Pull recent orders (stub empty). */
  Map<String, Object> pullOrders(int limit);
}
