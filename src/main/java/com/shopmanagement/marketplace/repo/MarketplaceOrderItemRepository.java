package com.shopmanagement.marketplace.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.marketplace.domain.MarketplaceOrderItem;

public interface MarketplaceOrderItemRepository extends JpaRepository<MarketplaceOrderItem, Long> {

  List<MarketplaceOrderItem> findByOrderIdOrderByIdAsc(Long orderId);
}
