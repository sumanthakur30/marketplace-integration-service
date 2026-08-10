package com.shopmanagement.marketplace.entitlement;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.shopmanagement.marketplace.config.MarketplaceProperties;
import com.shopmanagement.marketplace.support.TenantIds;

/**
 * Tenant/feature gate. When {@code marketplace.entitlement.enabled=false} (default), checks are
 * skipped for local scaffolding. Production: enable + require shop {@code MARKETPLACE} module.
 */
@Component
public class MarketplaceEntitlementGuard {

  private final MarketplaceProperties properties;
  private final ShopModuleEntitlementClient shopModuleEntitlementClient;

  public MarketplaceEntitlementGuard(
      MarketplaceProperties properties, ShopModuleEntitlementClient shopModuleEntitlementClient) {
    this.properties = properties;
    this.shopModuleEntitlementClient = shopModuleEntitlementClient;
  }

  public void requireModule() {
    if (!properties.getEntitlement().isEnabled()) {
      return;
    }
    String shopId = TenantIds.shopOrNull();
    if (shopId == null || shopId.isBlank()) {
      shopId = TenantIds.require();
    }
    if (!shopModuleEntitlementClient.isMarketplaceModuleEnabled(shopId)) {
      throw new ResponseStatusException(
          HttpStatus.PAYMENT_REQUIRED,
          "Marketplace Omnichannel is not enabled for this shop (module MARKETPLACE OFF)");
    }
  }

  public Map<String, Object> snapshot() {
    MarketplaceProperties.Entitlement e = properties.getEntitlement();
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("entitlementChecksEnabled", e.isEnabled());
    out.put("moduleCode", e.getModuleCode());
    out.put("masterFlag", e.getFlag());
    out.put("amazonFlag", e.getAmazonFlag());
    out.put("flipkartFlag", e.getFlipkartFlag());
    out.put("shopifyFlag", e.getShopifyFlag());
    out.put("websiteFlag", e.getWebsiteFlag());
    out.put(
        "channels",
        Map.of(
            "AMAZON", properties.getChannels().getAmazon().isEnabled(),
            "FLIPKART", properties.getChannels().getFlipkart().isEnabled(),
            "SHOPIFY", properties.getChannels().getShopify().isEnabled(),
            "WEBSITE", properties.getChannels().getWebsite().isEnabled()));
    out.put("reserveOnIngest", properties.getStock().isReserveOnIngest());
    out.put(
        "note",
        "Optional paid add-on. OFF by default — does not change POS/inventory for unentitled tenants.");
    return out;
  }
}
