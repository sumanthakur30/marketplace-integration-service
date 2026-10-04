package com.shopmanagement.marketplace.repo;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.marketplace.domain.MarketplaceChannelAudit;

public interface MarketplaceChannelAuditRepository extends JpaRepository<MarketplaceChannelAudit, Long> {}
