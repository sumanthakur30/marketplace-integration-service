package com.shopmanagement.marketplace.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.marketplace.domain.MarketplaceInventorySync;

public interface MarketplaceInventorySyncRepository
    extends JpaRepository<MarketplaceInventorySync, Long> {

  Optional<MarketplaceInventorySync> findFirstByMappingIdOrderByIdDesc(Long mappingId);
}
