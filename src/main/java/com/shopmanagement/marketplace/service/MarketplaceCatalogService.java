package com.shopmanagement.marketplace.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.shopmanagement.marketplace.channel.MarketplaceChannelCode;
import com.shopmanagement.marketplace.domain.ChannelStatusMapping;
import com.shopmanagement.marketplace.domain.MarketplaceAccount;
import com.shopmanagement.marketplace.domain.MarketplaceChannel;
import com.shopmanagement.marketplace.domain.MarketplaceChannelAudit;
import com.shopmanagement.marketplace.domain.MarketplaceProductMapping;
import com.shopmanagement.marketplace.repo.ChannelStatusMappingRepository;
import com.shopmanagement.marketplace.repo.MarketplaceAccountRepository;
import com.shopmanagement.marketplace.repo.MarketplaceChannelAuditRepository;
import com.shopmanagement.marketplace.repo.MarketplaceChannelRepository;
import com.shopmanagement.marketplace.repo.MarketplaceProductMappingRepository;
import com.shopmanagement.marketplace.security.ChannelSecretStore;
import com.shopmanagement.marketplace.security.ChannelSecrets;
import com.shopmanagement.marketplace.security.ChannelSettings;
import com.shopmanagement.marketplace.security.ChannelStatusCatalog;
import com.shopmanagement.marketplace.support.TenantIds;
import com.shopmanagement.marketplace.web.dto.AccountDtos;
import com.shopmanagement.marketplace.web.dto.ChannelDtos;
import com.shopmanagement.marketplace.web.dto.MappingDtos;

@Service
public class MarketplaceCatalogService {

  private final MarketplaceAccountRepository accountRepository;
  private final MarketplaceChannelRepository channelRepository;
  private final MarketplaceProductMappingRepository mappingRepository;
  private final ChannelStatusMappingRepository statusMappingRepository;
  private final MarketplaceChannelAuditRepository auditRepository;
  private final ChannelSecretStore secretStore;

  public MarketplaceCatalogService(
      MarketplaceAccountRepository accountRepository,
      MarketplaceChannelRepository channelRepository,
      MarketplaceProductMappingRepository mappingRepository,
      ChannelStatusMappingRepository statusMappingRepository,
      MarketplaceChannelAuditRepository auditRepository,
      ChannelSecretStore secretStore) {
    this.accountRepository = accountRepository;
    this.channelRepository = channelRepository;
    this.mappingRepository = mappingRepository;
    this.statusMappingRepository = statusMappingRepository;
    this.auditRepository = auditRepository;
    this.secretStore = secretStore;
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
    boolean created = channel.getId() == null;
    if (!created && !channel.getAccountId().equals(account.getId())) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Channel already linked to another account");
    }
    channel.setTenantId(tenantId);
    channel.setAccountId(account.getId());
    channel.setChannelCode(code);
    if (req.enabled() != null) {
      channel.setEnabled(Boolean.TRUE.equals(req.enabled()));
    } else if (created) {
      channel.setEnabled(false);
    }
    if (req.externalSellerId() != null) {
      String seller = req.externalSellerId().trim();
      channel.setExternalSellerId(seller.isEmpty() ? null : seller);
    }
    if (req.credentialsRef() != null) {
      String ref = req.credentialsRef().trim();
      channel.setCredentialsRef(ref.isEmpty() ? null : ref);
    }
    if (created && (channel.getConnectionStatus() == null || channel.getConnectionStatus().isBlank())) {
      channel.setConnectionStatus("DISCONNECTED");
    }

    ChannelSecretStore.SecretRead current = secretStore.read(channel.getCredentialsCiphertext());
    boolean clear = Boolean.TRUE.equals(req.clearSecrets());
    if (current.unreadable && !clear) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Stored credentials could not be read and were left unchanged.");
    }
    Map<String, String> stored =
        ChannelSecrets.merge(new LinkedHashMap<>(current.secrets), ChannelSecrets.extractSecrets(channel.getConfigJson()), false);
    Map<String, Object> incomingSecrets = new LinkedHashMap<>();
    if (req.secrets() != null) {
      incomingSecrets.putAll(req.secrets());
    }
    if (req.config() != null) {
      incomingSecrets.putAll(ChannelSecrets.extractSecrets(req.config()));
    }
    Set<String> beforeNames = ChannelSecrets.fieldNames(stored);
    Map<String, String> merged = ChannelSecrets.merge(stored, incomingSecrets, clear);
    Set<String> afterNames = ChannelSecrets.fieldNames(merged);
    channel.setCredentialsCiphertext(secretStore.write(merged));

    Map<String, Object> existingPublic = ChannelSecrets.publicConfig(channel.getConfigJson());
    if (req.config() != null) {
      channel.setConfigJson(ChannelSettings.normalize(existingPublic, req.config()));
    } else {
      channel.setConfigJson(existingPublic.isEmpty() && created ? ChannelSettings.defaults() : existingPublic);
    }
    if (clear || merged.isEmpty()) {
      channel.setConnectionStatus("DISCONNECTED");
      if (clear) {
        channel.setEnabled(false);
      }
    }

    MarketplaceChannel saved = channelRepository.save(channel);
    if (created) {
      seedStatusMappings(tenantId, saved);
    }
    audit(saved, "CHANNEL_SAVED", Map.of("channelCode", code, "created", created));
    if (clear || !beforeNames.equals(afterNames)) {
      audit(
          saved,
          "SECRETS_UPDATED",
          Map.of("credentialFieldNames", new ArrayList<>(afterNames), "cleared", clear));
    }
    return toChannelMap(saved);
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> listStatusMappings(Long channelId) {
    MarketplaceChannel channel = requireChannel(channelId);
    return statusMappingRepository
        .findByTenantIdAndChannelIdOrderByIdAsc(channel.getTenantId(), channel.getId())
        .stream()
        .map(this::toStatusMap)
        .collect(Collectors.toList());
  }

  @Transactional
  public List<Map<String, Object>> replaceStatusMappings(
      Long channelId, ChannelDtos.StatusMappingReplaceRequest body) {
    MarketplaceChannel channel = requireChannel(channelId);
    if (body == null || body.mappings() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "mappings required");
    }
    List<ChannelStatusMapping> rows = new ArrayList<>();
    Set<String> seen = new LinkedHashSet<>();
    for (ChannelDtos.StatusMappingRow row : body.mappings()) {
      if (row == null || row.externalStatus() == null || row.externalStatus().isBlank()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "external status required");
      }
      String external = row.externalStatus().trim();
      if (external.length() > 64) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "external status is too long");
      }
      String key = external.toLowerCase(Locale.ROOT);
      if (!seen.add(key)) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate external status");
      }
      String internal;
      try {
        internal = ChannelStatusCatalog.requireInternal(row.internalStatus());
      } catch (IllegalArgumentException ex) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported internal status");
      }
      ChannelStatusMapping mapping = new ChannelStatusMapping();
      mapping.setTenantId(channel.getTenantId());
      mapping.setChannelId(channel.getId());
      mapping.setExternalStatus(external);
      mapping.setInternalStatus(internal);
      rows.add(mapping);
    }
    statusMappingRepository.deleteByTenantIdAndChannelId(channel.getTenantId(), channel.getId());
    statusMappingRepository.flush();
    List<ChannelStatusMapping> saved = statusMappingRepository.saveAll(rows);
    audit(channel, "STATUS_MAP_SAVED", Map.of("count", saved.size()));
    return saved.stream().map(this::toStatusMap).collect(Collectors.toList());
  }

  @Transactional
  public Map<String, Object> connect(Long channelId) {
    MarketplaceChannel channel = requireChannel(channelId);
    ChannelSecretStore.SecretRead current = secretStore.read(channel.getCredentialsCiphertext());
    if (current.unreadable) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Stored credentials could not be read. No marketplace was called.");
    }
    Map<String, String> stored =
        ChannelSecrets.merge(new LinkedHashMap<>(current.secrets), ChannelSecrets.extractSecrets(channel.getConfigJson()), false);
    if (stored.isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Store credentials before connecting. No marketplace was called.");
    }
    channel.setCredentialsCiphertext(secretStore.write(stored));
    channel.setConfigJson(ChannelSecrets.publicConfig(channel.getConfigJson()));
    channel.setConnectionStatus("CONNECTED");
    channel.setEnabled(true);
    MarketplaceChannel saved = channelRepository.save(channel);
    audit(saved, "CONNECTED", Map.of("credentialFieldNames", new ArrayList<>(ChannelSecrets.fieldNames(stored))));
    return toChannelMap(saved);
  }

  @Transactional
  public Map<String, Object> disconnect(Long channelId) {
    MarketplaceChannel channel = requireChannel(channelId);
    channel.setConnectionStatus("DISCONNECTED");
    channel.setEnabled(false);
    boolean credentialsKept =
        channel.getCredentialsCiphertext() != null && !channel.getCredentialsCiphertext().isBlank();
    MarketplaceChannel saved = channelRepository.save(channel);
    audit(saved, "DISCONNECTED", Map.of("credentialsKept", credentialsKept));
    return toChannelMap(saved);
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

  private MarketplaceChannel requireChannel(Long channelId) {
    if (channelId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "channel id required");
    }
    return channelRepository
        .findByIdAndTenantIdAndDeletedAtIsNull(channelId, TenantIds.require())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Channel not found"));
  }

  private void seedStatusMappings(String tenantId, MarketplaceChannel channel) {
    List<ChannelStatusMapping> rows = new ArrayList<>();
    for (ChannelStatusCatalog.Seed seed : ChannelStatusCatalog.seedFor(channel.getChannelCode())) {
      ChannelStatusMapping mapping = new ChannelStatusMapping();
      mapping.setTenantId(tenantId);
      mapping.setChannelId(channel.getId());
      mapping.setExternalStatus(seed.externalStatus());
      mapping.setInternalStatus(seed.internalStatus());
      rows.add(mapping);
    }
    if (!rows.isEmpty()) {
      statusMappingRepository.saveAll(rows);
    }
  }

  private void audit(MarketplaceChannel channel, String action, Map<String, Object> detail) {
    MarketplaceChannelAudit row = new MarketplaceChannelAudit();
    row.setTenantId(channel.getTenantId());
    row.setShopId(TenantIds.shopOrNull());
    row.setChannelId(channel.getId());
    row.setAction(action);
    String actor = TenantIds.userOrNull();
    row.setActor(actor == null ? "unknown" : actor);
    row.setDetailJson(new LinkedHashMap<>(detail));
    auditRepository.save(row);
  }

  private static String normalizeChannel(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "channelCode required");
    }
    String code = raw.trim().toUpperCase(Locale.ROOT);
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
    ChannelSecretStore.SecretRead current = secretStore.read(c.getCredentialsCiphertext());
    Map<String, String> secrets =
        current.unreadable
            ? new LinkedHashMap<>()
            : ChannelSecrets.merge(current.secrets, ChannelSecrets.extractSecrets(c.getConfigJson()), false);
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", c.getId());
    m.put("tenantId", c.getTenantId());
    m.put("accountId", c.getAccountId());
    m.put("channelCode", c.getChannelCode());
    m.put("enabled", c.isEnabled());
    m.put("connectionStatus", c.getConnectionStatus() == null ? "DISCONNECTED" : c.getConnectionStatus());
    m.put("externalSellerId", c.getExternalSellerId());
    m.put("credentialsRef", c.getCredentialsRef());
    m.put("credentialsConfigured", current.unreadable || !secrets.isEmpty());
    m.put(
        "credentialFieldNames",
        current.unreadable ? List.of() : new ArrayList<>(ChannelSecrets.fieldNames(secrets)));
    m.put("config", ChannelSecrets.publicConfig(c.getConfigJson()));
    m.put("lastSyncAt", c.getLastSyncAt());
    return m;
  }

  private Map<String, Object> toStatusMap(ChannelStatusMapping row) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", row.getId());
    m.put("channelId", row.getChannelId());
    m.put("externalStatus", row.getExternalStatus());
    m.put("internalStatus", row.getInternalStatus());
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
