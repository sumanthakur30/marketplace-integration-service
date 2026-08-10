package com.shopmanagement.marketplace.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.shopmanagement.marketplace.channel.MarketplaceChannelCode;
import com.shopmanagement.marketplace.domain.MarketplaceAccount;
import com.shopmanagement.marketplace.domain.MarketplaceChannel;
import com.shopmanagement.marketplace.domain.MarketplaceProductMapping;
import com.shopmanagement.marketplace.repo.MarketplaceAccountRepository;
import com.shopmanagement.marketplace.repo.MarketplaceChannelRepository;
import com.shopmanagement.marketplace.repo.MarketplaceProductMappingRepository;
import com.shopmanagement.marketplace.support.TenantIds;
import com.shopmanagement.marketplace.web.dto.AccountDtos;
import com.shopmanagement.marketplace.web.dto.ChannelDtos;
import com.shopmanagement.marketplace.web.dto.MappingDtos;

@Service
public class MarketplaceCatalogService {

  private final MarketplaceAccountRepository accountRepository;
  private final MarketplaceChannelRepository channelRepository;
  private final MarketplaceProductMappingRepository mappingRepository;

  public MarketplaceCatalogService(
      MarketplaceAccountRepository accountRepository,
      MarketplaceChannelRepository channelRepository,
      MarketplaceProductMappingRepository mappingRepository) {
    this.accountRepository = accountRepository;
    this.channelRepository = channelRepository;
    this.mappingRepository = mappingRepository;
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> listAccounts() {
    return accountRepository.findByTenantIdAndDeletedAtIsNullOrderByIdAsc(TenantIds.require()).stream()
        .map(this::toAccountMap)
        .collect(Collectors.toList());
  }

  @Transactional
  public Map<String, Object> upsertAccount(AccountDtos.UpsertRequest req) {
    String tenantId = TenantIds.require();
    String shopId =
        req.shopId() != null && !req.shopId().isBlank()
            ? req.shopId().trim()
            : requireShopId();
    MarketplaceAccount account =
        accountRepository
            .findByTenantIdAndShopIdAndDeletedAtIsNull(tenantId, shopId)
            .orElseGet(MarketplaceAccount::new);
    account.setTenantId(tenantId);
    account.setShopId(shopId);
    account.setDisplayName(
        req.displayName() == null || req.displayName().isBlank()
            ? "Marketplace — " + shopId
            : req.displayName().trim());
    if (req.status() != null && !req.status().isBlank()) {
      account.setStatus(req.status().trim().toUpperCase());
    }
    if (req.settings() != null) {
      account.setSettingsJson(new LinkedHashMap<>(req.settings()));
    }
    return toAccountMap(accountRepository.save(account));
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> listChannels(Long accountId) {
    String tenantId = TenantIds.require();
    List<MarketplaceChannel> channels =
        accountId == null
            ? channelRepository.findByTenantIdAndDeletedAtIsNullOrderByIdAsc(tenantId)
            : channelRepository.findByTenantIdAndAccountIdAndDeletedAtIsNullOrderByIdAsc(
                tenantId, accountId);
    return channels.stream().map(this::toChannelMap).collect(Collectors.toList());
  }

  @Transactional
  public Map<String, Object> upsertChannel(ChannelDtos.UpsertRequest req) {
    String tenantId = TenantIds.require();
    MarketplaceAccount account =
        accountRepository
            .findByIdAndTenantIdAndDeletedAtIsNull(req.accountId(), tenantId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
    String code = normalizeChannel(req.channelCode());
    MarketplaceChannel channel =
        channelRepository
            .findByTenantIdAndChannelCodeAndDeletedAtIsNull(tenantId, code)
            .orElseGet(MarketplaceChannel::new);
    if (channel.getId() != null && !channel.getAccountId().equals(account.getId())) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Channel already linked to another account");
    }
    channel.setTenantId(tenantId);
    channel.setAccountId(account.getId());
    channel.setChannelCode(code);
    channel.setEnabled(Boolean.TRUE.equals(req.enabled()));
    channel.setExternalSellerId(req.externalSellerId());
    channel.setCredentialsRef(req.credentialsRef());
    if (req.config() != null) {
      channel.setConfigJson(new LinkedHashMap<>(req.config()));
    }
    return toChannelMap(channelRepository.save(channel));
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> listMappings(Long channelId) {
    String tenantId = TenantIds.require();
    List<MarketplaceProductMapping> rows =
        channelId == null
            ? mappingRepository.findByTenantIdAndDeletedAtIsNullOrderByIdAsc(tenantId)
            : mappingRepository.findByTenantIdAndChannelIdAndDeletedAtIsNullOrderByIdAsc(
                tenantId, channelId);
    return rows.stream().map(this::toMappingMap).collect(Collectors.toList());
  }

  @Transactional
  public Map<String, Object> upsertMapping(MappingDtos.UpsertRequest req) {
    String tenantId = TenantIds.require();
    String shopId =
        req.shopId() != null && !req.shopId().isBlank() ? req.shopId().trim() : requireShopId();
    MarketplaceChannel channel =
        channelRepository
            .findByIdAndTenantIdAndDeletedAtIsNull(req.channelId(), tenantId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Channel not found"));
    if (req.productId() == null || req.channelListingId() == null || req.channelListingId().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "productId and channelListingId required");
    }
    MarketplaceProductMapping mapping =
        mappingRepository
            .findByChannelIdAndChannelListingIdAndDeletedAtIsNull(
                channel.getId(), req.channelListingId().trim())
            .orElseGet(MarketplaceProductMapping::new);
    mapping.setTenantId(tenantId);
    mapping.setShopId(shopId);
    mapping.setChannelId(channel.getId());
    mapping.setProductId(req.productId());
    mapping.setSkuCode(req.skuCode());
    mapping.setChannelListingId(req.channelListingId().trim());
    mapping.setChannelSku(req.channelSku());
    mapping.setSyncInventory(req.syncInventory() == null || req.syncInventory());
    mapping.setAllocationQty(req.allocationQty());
    if (req.attributes() != null) {
      mapping.setAttributes(new LinkedHashMap<>(req.attributes()));
    }
    return toMappingMap(mappingRepository.save(mapping));
  }

  @Transactional
  public void softDeleteMapping(Long id) {
    String tenantId = TenantIds.require();
    MarketplaceProductMapping mapping =
        mappingRepository
            .findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mapping not found"));
    mapping.setDeletedAt(Instant.now());
    mappingRepository.save(mapping);
  }

  private static String normalizeChannel(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "channelCode required");
    }
    String code = raw.trim().toUpperCase();
    try {
      MarketplaceChannelCode.valueOf(code);
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported channelCode: " + code);
    }
    return code;
  }

  private static String requireShopId() {
    String shopId = TenantIds.shopOrNull();
    if (shopId == null || shopId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Shop-Id header required");
    }
    return shopId;
  }

  private Map<String, Object> toAccountMap(MarketplaceAccount a) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", a.getId());
    m.put("tenantId", a.getTenantId());
    m.put("shopId", a.getShopId());
    m.put("displayName", a.getDisplayName());
    m.put("status", a.getStatus());
    m.put("settings", a.getSettingsJson());
    return m;
  }

  private Map<String, Object> toChannelMap(MarketplaceChannel c) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", c.getId());
    m.put("tenantId", c.getTenantId());
    m.put("accountId", c.getAccountId());
    m.put("channelCode", c.getChannelCode());
    m.put("enabled", c.isEnabled());
    m.put("externalSellerId", c.getExternalSellerId());
    m.put("credentialsRef", c.getCredentialsRef());
    m.put("config", c.getConfigJson());
    m.put("lastSyncAt", c.getLastSyncAt());
    return m;
  }

  private Map<String, Object> toMappingMap(MarketplaceProductMapping map) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", map.getId());
    m.put("tenantId", map.getTenantId());
    m.put("shopId", map.getShopId());
    m.put("channelId", map.getChannelId());
    m.put("productId", map.getProductId());
    m.put("skuCode", map.getSkuCode());
    m.put("channelListingId", map.getChannelListingId());
    m.put("channelSku", map.getChannelSku());
    m.put("syncInventory", map.isSyncInventory());
    m.put("allocationQty", map.getAllocationQty());
    m.put("attributes", map.getAttributes());
    return m;
  }
}
