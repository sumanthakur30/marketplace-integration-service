package com.shopmanagement.marketplace.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.marketplace.domain.MarketplaceProductMapping;

public interface MarketplaceProductMappingRepository
    extends JpaRepository<MarketplaceProductMapping, Long> {

  List<MarketplaceProductMapping> findByTenantIdAndDeletedAtIsNullOrderByIdAsc(String tenantId);

  List<MarketplaceProductMapping> findByTenantIdAndChannelIdAndDeletedAtIsNullOrderByIdAsc(
      String tenantId, Long channelId);

  Optional<MarketplaceProductMapping> findByIdAndTenantIdAndDeletedAtIsNull(Long id, String tenantId);

  Optional<MarketplaceProductMapping> findByChannelIdAndChannelListingIdAndDeletedAtIsNull(
      Long channelId, String channelListingId);

  Optional<MarketplaceProductMapping> findByChannelIdAndChannelSkuAndDeletedAtIsNull(
      Long channelId, String channelSku);
}
