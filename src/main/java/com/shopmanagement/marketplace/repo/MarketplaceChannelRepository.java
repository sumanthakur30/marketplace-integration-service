package com.shopmanagement.marketplace.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.marketplace.domain.MarketplaceChannel;

public interface MarketplaceChannelRepository extends JpaRepository<MarketplaceChannel, Long> {

  List<MarketplaceChannel> findByTenantIdAndDeletedAtIsNullOrderByIdAsc(String tenantId);

  List<MarketplaceChannel> findByTenantIdAndAccountIdAndDeletedAtIsNullOrderByIdAsc(
      String tenantId, Long accountId);

  Optional<MarketplaceChannel> findByIdAndTenantIdAndDeletedAtIsNull(Long id, String tenantId);

  Optional<MarketplaceChannel> findByTenantIdAndChannelCodeAndDeletedAtIsNull(
      String tenantId, String channelCode);
}
