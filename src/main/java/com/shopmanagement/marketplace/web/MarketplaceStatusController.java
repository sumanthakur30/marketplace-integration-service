package com.shopmanagement.marketplace.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shopmanagement.marketplace.channel.MarketplaceChannelAdapter;
import com.shopmanagement.marketplace.entitlement.MarketplaceEntitlementGuard;
import com.shopmanagement.marketplace.support.TenantIds;

@RestController
@RequestMapping("/api/v1/marketplace")
public class MarketplaceStatusController {

  private final MarketplaceEntitlementGuard entitlementGuard;
  private final List<MarketplaceChannelAdapter> adapters;

  public MarketplaceStatusController(
      MarketplaceEntitlementGuard entitlementGuard, List<MarketplaceChannelAdapter> adapters) {
    this.entitlementGuard = entitlementGuard;
    this.adapters = adapters;
  }

  @GetMapping("/status")
  public Map<String, Object> status() {
    entitlementGuard.requireModule();
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("service", "marketplace-integration-service");
    out.put("module", "MARKETPLACE");
    out.put("tenantId", TenantIds.require());
    out.put("shopId", TenantIds.shopOrNull());
    out.put("entitlement", entitlementGuard.snapshot());
    out.put(
        "adapters",
        adapters.stream()
            .map(
                a ->
                    Map.of(
                        "channel", a.code().name(),
                        "configured", a.isConfigured()))
            .collect(Collectors.toList()));
    out.put(
        "inventoryContract",
        Map.of(
            "sourceOfTruth", "stock-service",
            "physical", "stock.quantity (on-hand)",
            "reserved", "stock.reserved",
            "available", "quantity - reserved",
            "marketplaceAllocation", "marketplace_inventory_sync.allocated_qty (this DB only)",
            "existingClients", "unchanged when MARKETPLACE module OFF"));
    return out;
  }

  @GetMapping("/entitlements")
  public Map<String, Object> entitlements() {
    return entitlementGuard.snapshot();
  }
}
