package com.shopmanagement.marketplace.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.marketplace.domain.MarketplaceSettlement;

public interface MarketplaceSettlementRepository extends JpaRepository<MarketplaceSettlement, Long> {

  Optional<MarketplaceSettlement> findByChannelIdAndExternalSettlementId(Long channelId, String externalSettlementId);
}
