package com.shopmanagement.marketplace.web;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.shopmanagement.marketplace.entitlement.MarketplaceEntitlementGuard;
import com.shopmanagement.marketplace.mapping.SkuMatch;
import com.shopmanagement.marketplace.security.ChannelSecrets;
import com.shopmanagement.marketplace.security.MarketplaceAccessGuard;
import com.shopmanagement.marketplace.service.MarketplaceCatalogService;
import com.shopmanagement.marketplace.service.MarketplaceSellableService;
import com.shopmanagement.marketplace.web.dto.AccountDtos;
import com.shopmanagement.marketplace.web.dto.ChannelDtos;
import com.shopmanagement.marketplace.web.dto.MappingDtos;

@RestController
@RequestMapping("/api/v1/marketplace")
public class MarketplaceCatalogController {

  private final MarketplaceEntitlementGuard entitlementGuard;
  private final MarketplaceAccessGuard accessGuard;
  private final MarketplaceCatalogService catalogService;
  private final MarketplaceSellableService sellableService;

  public MarketplaceCatalogController(
      MarketplaceEntitlementGuard entitlementGuard,
      MarketplaceAccessGuard accessGuard,
      MarketplaceCatalogService catalogService,
      MarketplaceSellableService sellableService) {
    this.entitlementGuard = entitlementGuard;
    this.accessGuard = accessGuard;
    this.catalogService = catalogService;
    this.sellableService = sellableService;
  }

  @GetMapping("/accounts")
  public List<Map<String, Object>> listAccounts() {
    entitlementGuard.requireModule();
    accessGuard.requireView();
    return catalogService.listAccounts();
  }

  @PostMapping("/accounts")
  public Map<String, Object> upsertAccount(@RequestBody AccountDtos.UpsertRequest body) {
    entitlementGuard.requireModule();
    accessGuard.requireManage();
    return catalogService.upsertAccount(body);
  }

  @GetMapping("/channels")
  public List<Map<String, Object>> listChannels(@RequestParam(required = false) Long accountId) {
    entitlementGuard.requireModule();
    accessGuard.requireView();
    return catalogService.listChannels(accountId);
  }

  @PostMapping("/channels")
  public Map<String, Object> upsertChannel(@RequestBody ChannelDtos.UpsertRequest body) {
    entitlementGuard.requireModule();
    accessGuard.requireManage();
    if (body != null && (Boolean.TRUE.equals(body.clearSecrets()) || hasSecretValue(body))) {
      accessGuard.requireConnect();
    }
    return catalogService.upsertChannel(body);
  }

  @GetMapping("/channels/{id}/status-mappings")
  public List<Map<String, Object>> listStatusMappings(@PathVariable Long id) {
    entitlementGuard.requireModule();
    accessGuard.requireView();
    return catalogService.listStatusMappings(id);
  }

  @PutMapping("/channels/{id}/status-mappings")
  public List<Map<String, Object>> replaceStatusMappings(
      @PathVariable Long id, @RequestBody ChannelDtos.StatusMappingReplaceRequest body) {
    entitlementGuard.requireModule();
    accessGuard.requireManage();
    return catalogService.replaceStatusMappings(id, body);
  }

  @PostMapping("/channels/{id}/connect")
  public Map<String, Object> connect(@PathVariable Long id) {
    entitlementGuard.requireModule();
    accessGuard.requireConnect();
    return catalogService.connect(id);
  }

  @PostMapping("/channels/{id}/disconnect")
  public Map<String, Object> disconnect(@PathVariable Long id) {
    entitlementGuard.requireModule();
    accessGuard.requireConnect();
    return catalogService.disconnect(id);
  }

  @GetMapping("/channels/{id}/sellable")
  public List<Map<String, Object>> sellable(@PathVariable Long id) {
    entitlementGuard.requireModule();
    accessGuard.requireMappingView();
    return sellableService.quote(id);
  }

  @GetMapping("/mappings")
  public List<Map<String, Object>> listMappings(@RequestParam(required = false) Long channelId) {
    entitlementGuard.requireModule();
    accessGuard.requireMappingView();
    return catalogService.listMappings(channelId);
  }

  @PostMapping("/mappings/suggest")
  public Map<String, Object> suggestMapping(@RequestBody MappingDtos.SuggestRequest body) {
    entitlementGuard.requireModule();
    accessGuard.requireMappingView();
    String sku = body == null ? null : body.channelSku();
    java.util.List<SkuMatch.ProductRef> products = new java.util.ArrayList<>();
    if (body != null && body.products() != null) {
      for (MappingDtos.SkuMatchProduct product : body.products()) {
        if (product == null) {
          continue;
        }
        products.add(new SkuMatch.ProductRef(product.productId(), product.code(), product.barcode()));
      }
    }
    return SkuMatch.toMap(SkuMatch.match(sku, products));
  }

  @PostMapping("/mappings")
  public Map<String, Object> upsertMapping(@RequestBody MappingDtos.UpsertRequest body) {
    entitlementGuard.requireModule();
    accessGuard.requireProductMapping();
    return catalogService.upsertMapping(body);
  }

  @DeleteMapping("/mappings/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteMapping(@PathVariable Long id) {
    entitlementGuard.requireModule();
    accessGuard.requireProductMapping();
    catalogService.softDeleteMapping(id);
  }

  private static boolean hasSecretValue(ChannelDtos.UpsertRequest body) {
    if (body.secrets() != null && !ChannelSecrets.extractSecrets(body.secrets()).isEmpty()) {
      return true;
    }
    return body.config() != null && !ChannelSecrets.extractSecrets(body.config()).isEmpty();
  }
}
