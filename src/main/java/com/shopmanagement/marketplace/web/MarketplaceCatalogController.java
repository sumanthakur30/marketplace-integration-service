package com.shopmanagement.marketplace.web;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.shopmanagement.marketplace.entitlement.MarketplaceEntitlementGuard;
import com.shopmanagement.marketplace.service.MarketplaceCatalogService;
import com.shopmanagement.marketplace.web.dto.AccountDtos;
import com.shopmanagement.marketplace.web.dto.ChannelDtos;
import com.shopmanagement.marketplace.web.dto.MappingDtos;

@RestController
@RequestMapping("/api/v1/marketplace")
public class MarketplaceCatalogController {

  private final MarketplaceEntitlementGuard entitlementGuard;
  private final MarketplaceCatalogService catalogService;

  public MarketplaceCatalogController(
      MarketplaceEntitlementGuard entitlementGuard, MarketplaceCatalogService catalogService) {
    this.entitlementGuard = entitlementGuard;
    this.catalogService = catalogService;
  }

  @GetMapping("/accounts")
  public List<Map<String, Object>> listAccounts() {
    entitlementGuard.requireModule();
    return catalogService.listAccounts();
  }

  @PostMapping("/accounts")
  public Map<String, Object> upsertAccount(@RequestBody AccountDtos.UpsertRequest body) {
    entitlementGuard.requireModule();
    return catalogService.upsertAccount(body);
  }

  @GetMapping("/channels")
  public List<Map<String, Object>> listChannels(@RequestParam(required = false) Long accountId) {
    entitlementGuard.requireModule();
    return catalogService.listChannels(accountId);
  }

  @PostMapping("/channels")
  public Map<String, Object> upsertChannel(@RequestBody ChannelDtos.UpsertRequest body) {
    entitlementGuard.requireModule();
    return catalogService.upsertChannel(body);
  }

  @GetMapping("/mappings")
  public List<Map<String, Object>> listMappings(@RequestParam(required = false) Long channelId) {
    entitlementGuard.requireModule();
    return catalogService.listMappings(channelId);
  }

  @PostMapping("/mappings")
  public Map<String, Object> upsertMapping(@RequestBody MappingDtos.UpsertRequest body) {
    entitlementGuard.requireModule();
    return catalogService.upsertMapping(body);
  }

  @DeleteMapping("/mappings/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteMapping(@PathVariable Long id) {
    entitlementGuard.requireModule();
    catalogService.softDeleteMapping(id);
  }
}
