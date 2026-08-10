package com.shopmanagement.marketplace.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.marketplace.domain.MarketplaceOrder;

public interface MarketplaceOrderRepository extends JpaRepository<MarketplaceOrder, Long> {

  List<MarketplaceOrder> findByTenantIdOrderByIdDesc(String tenantId);

  Optional<MarketplaceOrder> findByIdAndTenantId(Long id, String tenantId);

  Optional<MarketplaceOrder> findByChannelIdAndExternalOrderId(Long channelId, String externalOrderId);
}
