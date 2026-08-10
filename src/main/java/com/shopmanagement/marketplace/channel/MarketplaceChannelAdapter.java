package com.shopmanagement.marketplace.channel;

import java.util.Map;

/** Channel adapter SPI — real Amazon/Flipkart/Shopify SDKs plug in later without touching ERP. */
public interface MarketplaceChannelAdapter {

  MarketplaceChannelCode code();

  boolean isConfigured();

  /** Push sellable qty for a listing. Stub returns accepted=false until credentials wired. */
  Map<String, Object> pushInventory(String listingId, String channelSku, double availableQty);

  /** Pull recent orders (stub empty). */
  Map<String, Object> pullOrders(int limit);
}
