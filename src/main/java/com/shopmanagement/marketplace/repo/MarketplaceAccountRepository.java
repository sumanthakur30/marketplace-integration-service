package com.shopmanagement.marketplace.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.marketplace.domain.MarketplaceAccount;

public interface MarketplaceAccountRepository extends JpaRepository<MarketplaceAccount, Long> {

  List<MarketplaceAccount> findByTenantIdAndDeletedAtIsNullOrderByIdAsc(String tenantId);

  Optional<MarketplaceAccount> findByIdAndTenantIdAndDeletedAtIsNull(Long id, String tenantId);

  Optional<MarketplaceAccount> findByTenantIdAndShopIdAndDeletedAtIsNull(String tenantId, String shopId);
}
