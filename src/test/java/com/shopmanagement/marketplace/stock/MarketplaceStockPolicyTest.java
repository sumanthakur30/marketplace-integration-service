package com.shopmanagement.marketplace.stock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

class MarketplaceStockPolicyTest {

  @Test
  void confirmedAmazonSaleHoldsStockAndShippedCommitsIt() {
    assertTrue(MarketplaceStockPolicy.holdsStock("UNSHIPPED"));
    assertFalse(MarketplaceStockPolicy.commitsStock("UNSHIPPED"));
    assertTrue(MarketplaceStockPolicy.holdsStock("SHIPPED"));
    assertTrue(MarketplaceStockPolicy.commitsStock("SHIPPED"));
    assertFalse(MarketplaceStockPolicy.holdsStock("PENDING"));
    assertTrue(MarketplaceStockPolicy.releasesStock("CANCELED"));
  }

  @Test
  void readsAmazonStatusFromTheOrderPayload() {
    assertTrue(MarketplaceStockPolicy.holdsStock(MarketplaceStockPolicy.externalStatus(Map.of("externalStatus", "Unshipped"))));
    assertFalse(MarketplaceStockPolicy.holdsStock(MarketplaceStockPolicy.externalStatus(Map.of())));
  }
}
