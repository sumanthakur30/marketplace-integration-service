package com.shopmanagement.marketplace.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.shopmanagement.marketplace.channel.InventoryPushRequest;
import com.shopmanagement.marketplace.channel.MarketplaceChannelAdapter;
import com.shopmanagement.marketplace.channel.MarketplaceChannelCode;
import com.shopmanagement.marketplace.domain.MarketplaceChannel;
import com.shopmanagement.marketplace.domain.MarketplaceInventorySync;
import com.shopmanagement.marketplace.domain.MarketplaceProductMapping;
import com.shopmanagement.marketplace.repo.MarketplaceChannelRepository;
import com.shopmanagement.marketplace.repo.MarketplaceInventorySyncRepository;
import com.shopmanagement.marketplace.repo.MarketplaceProductMappingRepository;
import com.shopmanagement.marketplace.security.ChannelSecretStore;
import com.shopmanagement.marketplace.security.ChannelSecrets;
import com.shopmanagement.marketplace.stock.StockAvailabilityClient;
import com.shopmanagement.marketplace.support.TenantIds;

/**
 * Pushes sellable qty (stock available, capped by allocation) to channel adapters. Does not change
 * stock-service math — read only.
 */
@Service
public class MarketplaceInventorySyncService {

  private final MarketplaceProductMappingRepository mappingRepository;
  private final MarketplaceChannelRepository channelRepository;
  private final MarketplaceInventorySyncRepository inventorySyncRepository;
  private final StockAvailabilityClient stockAvailabilityClient;
  private final ChannelSecretStore secretStore;
  private final Map<MarketplaceChannelCode, MarketplaceChannelAdapter> adaptersByCode;

  public MarketplaceInventorySyncService(
      MarketplaceProductMappingRepository mappingRepository,
      MarketplaceChannelRepository channelRepository,
      MarketplaceInventorySyncRepository inventorySyncRepository,
      StockAvailabilityClient stockAvailabilityClient,
      ChannelSecretStore secretStore,
      List<MarketplaceChannelAdapter> adapters) {
    this.mappingRepository = mappingRepository;
    this.channelRepository = channelRepository;
    this.inventorySyncRepository = inventorySyncRepository;
    this.stockAvailabilityClient = stockAvailabilityClient;
    this.secretStore = secretStore;
    this.adaptersByCode =
        adapters.stream()
            .collect(Collectors.toMap(MarketplaceChannelAdapter::code, Function.identity()));
  }

  @Transactional
  public Map<String, Object> sync(Long channelId, Long mappingId) {
    String tenantId = TenantIds.require();
    String shopId = TenantIds.shopOrNull();
    if (shopId == null || shopId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Shop-Id required");
    }

    List<MarketplaceProductMapping> mappings;
    if (mappingId != null) {
      MarketplaceProductMapping one =
          mappingRepository
              .findByIdAndTenantIdAndDeletedAtIsNull(mappingId, tenantId)
              .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mapping not found"));
      mappings = List.of(one);
    } else if (channelId != null) {
      mappings =
          mappingRepository.findByTenantIdAndChannelIdAndDeletedAtIsNullOrderByIdAsc(
              tenantId, channelId);
    } else {
      mappings = mappingRepository.findByTenantIdAndDeletedAtIsNullOrderByIdAsc(tenantId);
    }

    List<Map<String, Object>> results = new ArrayList<>();
    int synced = 0;
    int errors = 0;
    int skipped = 0;
    for (MarketplaceProductMapping mapping : mappings) {
      Map<String, Object> row = syncOne(tenantId, shopId, mapping);
      results.add(row);
      String status = String.valueOf(row.get("syncStatus"));
      if ("SYNCED".equals(status)) {
        synced++;
      } else if ("ERROR".equals(status)) {
        errors++;
      } else {
        skipped++;
      }
    }

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("tenantId", tenantId);
    out.put("shopId", shopId);
    out.put("synced", synced);
    out.put("errors", errors);
    out.put("skipped", skipped);
    out.put("results", results);
    return out;
  }

  private Map<String, Object> syncOne(
      String tenantId, String shopId, MarketplaceProductMapping mapping) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("mappingId", mapping.getId());
    out.put("productId", mapping.getProductId());
    out.put("channelListingId", mapping.getChannelListingId());

    if (!mapping.isSyncInventory()) {
      out.put("syncStatus", "SKIPPED");
      out.put("reason", "syncInventory=false");
      persistSync(tenantId, mapping, BigDecimal.ZERO, BigDecimal.ZERO, "SKIPPED", "syncInventory=false");
      return out;
    }

    MarketplaceChannel channel =
        channelRepository
            .findByIdAndTenantIdAndDeletedAtIsNull(mapping.getChannelId(), tenantId)
            .orElse(null);
    if (channel == null || !channel.isEnabled()) {
      out.put("syncStatus", "SKIPPED");
      out.put("reason", "channel disabled or missing");
      persistSync(tenantId, mapping, BigDecimal.ZERO, BigDecimal.ZERO, "SKIPPED", "channel disabled");
      return out;
    }

    MarketplaceChannelCode code;
    try {
      code = MarketplaceChannelCode.valueOf(channel.getChannelCode());
    } catch (Exception ex) {
      out.put("syncStatus", "ERROR");
      out.put("reason", "unknown channel " + channel.getChannelCode());
      return out;
    }

    MarketplaceChannelAdapter adapter = adaptersByCode.get(code);
    if (adapter == null) {
      out.put("syncStatus", "ERROR");
      out.put("reason", "no adapter");
      return out;
    }

    double available =
        stockAvailabilityClient
            .availableQuantity(tenantId, shopId, mapping.getProductId())
            .orElse(0d);
    BigDecimal availableBd = BigDecimal.valueOf(available).setScale(3, RoundingMode.HALF_UP);
    BigDecimal allocated = mapping.getAllocationQty();
    BigDecimal pushQty = availableBd;
    if (allocated != null && allocated.signum() > 0 && allocated.compareTo(pushQty) < 0) {
      pushQty = allocated;
    }
    if (pushQty.signum() < 0) {
      pushQty = BigDecimal.ZERO;
    }

    InventoryPushRequest req =
        new InventoryPushRequest(
            mapping.getChannelListingId(),
            mapping.getChannelSku(),
            pushQty.doubleValue(),
            secretStore.adapterConfig(channel.getConfigJson(), channel.getCredentialsCiphertext()),
            mapping.getAttributes());
    Map<String, Object> pushResult = ChannelSecrets.scrub(adapter.pushInventory(req));
    boolean accepted = Boolean.TRUE.equals(pushResult.get("accepted"));
    String status = accepted ? "SYNCED" : "ERROR";
    String error =
        accepted ? null : String.valueOf(pushResult.getOrDefault("reason", pushResult.get("error")));

    MarketplaceInventorySync sync =
        persistSync(tenantId, mapping, availableBd, pushQty, status, error);
    out.put("syncStatus", status);
    out.put("availableQty", availableBd);
    out.put("allocatedQty", pushQty);
    out.put("channelPush", pushResult);
    out.put("inventorySyncId", sync.getId());
    return out;
  }

  private MarketplaceInventorySync persistSync(
      String tenantId,
      MarketplaceProductMapping mapping,
      BigDecimal available,
      BigDecimal allocated,
      String status,
      String error) {
    MarketplaceInventorySync sync =
        inventorySyncRepository
            .findFirstByMappingIdOrderByIdDesc(mapping.getId())
            .orElseGet(MarketplaceInventorySync::new);
    sync.setTenantId(tenantId);
    sync.setMappingId(mapping.getId());
    sync.setPhysicalQty(available);
    sync.setReservedQty(BigDecimal.ZERO);
    sync.setAvailableQty(available);
    sync.setAllocatedQty(allocated);
    sync.setChannelQty(allocated);
    sync.setSyncStatus(status);
    sync.setLastError(error);
    if ("SYNCED".equals(status)) {
      sync.setSyncedAt(Instant.now());
    }
    return inventorySyncRepository.save(sync);
  }
}
