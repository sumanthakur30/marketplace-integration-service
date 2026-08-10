package com.shopmanagement.marketplace.channel;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MarketplaceChannelCodeTest {

  @Test
  void knownChannelsParse() {
    assertThat(MarketplaceChannelCode.valueOf("AMAZON")).isEqualTo(MarketplaceChannelCode.AMAZON);
    assertThat(MarketplaceChannelCode.valueOf("FLIPKART")).isEqualTo(MarketplaceChannelCode.FLIPKART);
    assertThat(MarketplaceChannelCode.valueOf("SHOPIFY")).isEqualTo(MarketplaceChannelCode.SHOPIFY);
    assertThat(MarketplaceChannelCode.valueOf("WEBSITE")).isEqualTo(MarketplaceChannelCode.WEBSITE);
  }
}
