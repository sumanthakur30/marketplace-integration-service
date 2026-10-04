package com.shopmanagement.marketplace.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.marketplace.domain.MarketplaceReturn;

public interface MarketplaceReturnRepository extends JpaRepository<MarketplaceReturn, Long> {

  Optional<MarketplaceReturn> findByOrderIdAndExternalReturnId(Long orderId, String externalReturnId);
}
