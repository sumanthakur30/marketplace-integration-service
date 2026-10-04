package com.shopmanagement.marketplace.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.marketplace.domain.ChannelStatusMapping;

public interface ChannelStatusMappingRepository extends JpaRepository<ChannelStatusMapping, Long> {

  List<ChannelStatusMapping> findByTenantIdAndChannelIdOrderByIdAsc(String tenantId, Long channelId);

  void deleteByTenantIdAndChannelId(String tenantId, Long channelId);
}
