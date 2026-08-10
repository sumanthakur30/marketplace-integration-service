package com.shopmanagement.marketplace.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shopmanagement.marketplace.domain.MarketplaceWebhookEvent;

public interface MarketplaceWebhookEventRepository
    extends JpaRepository<MarketplaceWebhookEvent, Long> {

  List<MarketplaceWebhookEvent> findTop50ByProcessedFalseOrderByReceivedAtAsc();

  Optional<MarketplaceWebhookEvent> findByChannelCodeAndExternalIdAndEventType(
      String channelCode, String externalId, String eventType);
}
