package com.shopmanagement.marketplace.web;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shopmanagement.marketplace.entitlement.MarketplaceEntitlementGuard;
import com.shopmanagement.marketplace.service.MarketplaceInventorySyncService;
import com.shopmanagement.marketplace.stock.StockAvailabilityClient;
import com.shopmanagement.marketplace.support.TenantIds;

@RestController
@RequestMapping("/api/v1/marketplace/inventory")
public class MarketplaceInventoryController {

  private final MarketplaceEntitlementGuard entitlementGuard;
  private final StockAvailabilityClient stockAvailabilityClient;
  private final MarketplaceInventorySyncService inventorySyncService;

  public MarketplaceInventoryController(
      MarketplaceEntitlementGuard entitlementGuard,
      StockAvailabilityClient stockAvailabilityClient,
      MarketplaceInventorySyncService inventorySyncService) {
    this.entitlementGuard = entitlementGuard;
    this.stockAvailabilityClient = stockAvailabilityClient;
    this.inventorySyncService = inventorySyncService;
  }

  /** Preview sellable qty from stock-service for mapping / sync UI. No stock mutation. */
  @GetMapping("/products/{productId}/available")
  public Map<String, Object> available(@PathVariable long productId) {
    entitlementGuard.requireModule();
    String tenantId = TenantIds.require();
    String shopId = TenantIds.shopOrNull();
    Optional<Double> available =
        stockAvailabilityClient.availableQuantity(tenantId, shopId, productId);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("productId", productId);
    out.put("tenantId", tenantId);
    out.put("shopId", shopId == null ? "" : shopId);
    out.put("availableQuantity", available.orElse(null));
    out.put("source", "stock-service");
    out.put("mutatesStock", false);
    return out;
  }

  /**
   * Push available qty to channel listings (Shopify Admin or Amazon bridge). Caps by mapping
   * allocation_qty. Dry-run by default until channel credentials + dry-run=false.
   */
  @PostMapping("/sync")
  public Map<String, Object> sync(
      @RequestParam(required = false) Long channelId,
      @RequestParam(required = false) Long mappingId) {
    entitlementGuard.requireModule();
    return inventorySyncService.sync(channelId, mappingId);
  }
}
