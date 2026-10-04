package com.shopmanagement.marketplace.web;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shopmanagement.marketplace.entitlement.MarketplaceEntitlementGuard;
import com.shopmanagement.marketplace.security.MarketplaceAccessGuard;
import com.shopmanagement.marketplace.service.MarketplaceOrderService;
import com.shopmanagement.marketplace.web.dto.OrderDtos;

@RestController
@RequestMapping("/api/v1/marketplace/orders")
public class MarketplaceOrderController {

  private final MarketplaceEntitlementGuard entitlementGuard;
  private final MarketplaceAccessGuard accessGuard;
  private final MarketplaceOrderService orderService;

  public MarketplaceOrderController(
      MarketplaceEntitlementGuard entitlementGuard,
      MarketplaceAccessGuard accessGuard,
      MarketplaceOrderService orderService) {
    this.entitlementGuard = entitlementGuard;
    this.accessGuard = accessGuard;
    this.orderService = orderService;
  }

  @GetMapping
  public List<Map<String, Object>> list() {
    entitlementGuard.requireModule();
    accessGuard.requireOrderView();
    return orderService.listOrders();
  }

  @GetMapping("/{id}")
  public Map<String, Object> get(@PathVariable Long id) {
    entitlementGuard.requireModule();
    accessGuard.requireOrderView();
    return orderService.getOrder(id);
  }

  @PostMapping("/ingest")
  public Map<String, Object> ingest(@RequestBody OrderDtos.IngestRequest body) {
    entitlementGuard.requireModule();
    accessGuard.requireOrderManage();
    return orderService.ingest(body);
  }

  @PostMapping("/{id}/ship")
  public Map<String, Object> ship(@PathVariable Long id) {
    entitlementGuard.requireModule();
    accessGuard.requireOrderManage();
    return orderService.ship(id);
  }

  @PostMapping("/{id}/apply-mappings")
  public Map<String, Object> applyMappings(@PathVariable Long id) {
    entitlementGuard.requireModule();
    accessGuard.requireApplyMappings();
    return orderService.applyMappings(id);
  }

  @PostMapping("/{id}/cancel")
  public Map<String, Object> cancel(@PathVariable Long id) {
    entitlementGuard.requireModule();
    accessGuard.requireOrderManage();
    return orderService.cancel(id);
  }
}
