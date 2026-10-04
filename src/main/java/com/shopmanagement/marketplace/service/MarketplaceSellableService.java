package com.shopmanagement.marketplace.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.shopmanagement.marketplace.domain.MarketplaceChannel;
import com.shopmanagement.marketplace.domain.MarketplaceProductMapping;
import com.shopmanagement.marketplace.repo.MarketplaceChannelRepository;
import com.shopmanagement.marketplace.repo.MarketplaceProductMappingRepository;
import com.shopmanagement.marketplace.stock.SellableQty;
import com.shopmanagement.marketplace.stock.StockAvailabilityClient;
import com.shopmanagement.marketplace.support.TenantIds;

@Service
public class MarketplaceSellableService {

  private final MarketplaceChannelRepository channelRepository;
  private final MarketplaceProductMappingRepository mappingRepository;
  private final StockAvailabilityClient stockAvailabilityClient;

  public MarketplaceSellableService(
      MarketplaceChannelRepository channelRepository,
      MarketplaceProductMappingRepository mappingRepository,
      StockAvailabilityClient stockAvailabilityClient) {
    this.channelRepository = channelRepository;
    this.mappingRepository = mappingRepository;
    this.stockAvailabilityClient = stockAvailabilityClient;
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> quote(Long channelId) {
    String tenantId = TenantIds.require();
    String shopId = TenantIds.shopOrNull();
    MarketplaceChannel channel =
        channelRepository
            .findByIdAndTenantIdAndDeletedAtIsNull(channelId, tenantId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Channel not found"));
    int buffer = SellableQty.buffer(channel.getConfigJson() == null ? null : channel.getConfigJson().get("inventoryBufferQty"));
    List<Map<String, Object>> rows = new ArrayList<>();
    for (MarketplaceProductMapping mapping :
        mappingRepository.findByTenantIdAndChannelIdAndDeletedAtIsNullOrderByIdAsc(tenantId, channel.getId())) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("mappingId", mapping.getId());
      row.put("productId", mapping.getProductId());
      row.put("channelSku", mapping.getChannelSku());
      row.put("bufferQty", buffer);
      var available = stockAvailabilityClient.availableQuantity(tenantId, shopId, mapping.getProductId());
      if (available.isPresent()) {
        row.put("availableQty", available.get());
        row.put("sellableQty", SellableQty.units(available.get(), buffer));
      } else {
        row.put("availableQty", null);
        row.put("sellableQty", null);
      }
      rows.add(row);
    }
    return rows;
  }
}
