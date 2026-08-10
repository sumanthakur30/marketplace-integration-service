package com.shopmanagement.marketplace.channel;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.shopmanagement.marketplace.config.MarketplaceProperties;

@Component
public class AmazonChannelAdapter implements MarketplaceChannelAdapter {

  private final MarketplaceProperties properties;

  public AmazonChannelAdapter(MarketplaceProperties properties) {
    this.properties = properties;
  }

  @Override
  public MarketplaceChannelCode code() {
    return MarketplaceChannelCode.AMAZON;
  }

  @Override
  public boolean isConfigured() {
    return properties.getChannels().getAmazon().isEnabled();
  }

  @Override
  public Map<String, Object> pushInventory(String listingId, String channelSku, double availableQty) {
    return Map.of(
        "channel", code().name(),
        "accepted", false,
        "reason", "STUB_NOT_CONFIGURED",
        "listingId", listingId == null ? "" : listingId,
        "availableQty", availableQty);
  }

  @Override
  public Map<String, Object> pullOrders(int limit) {
    return Map.of("channel", code().name(), "orders", java.util.List.of(), "stub", true);
  }
}
