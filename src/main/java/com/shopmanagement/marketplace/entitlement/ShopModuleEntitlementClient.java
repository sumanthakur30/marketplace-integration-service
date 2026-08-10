package com.shopmanagement.marketplace.entitlement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import com.shopmanagement.marketplace.config.MarketplaceProperties;

/**
 * Probes shop-service effective-config for {@code ModuleCode.MARKETPLACE}. Tenants without the
 * module never get marketplace APIs when entitlement checks are enabled.
 */
@Component
public class ShopModuleEntitlementClient {

  private static final Logger log = LoggerFactory.getLogger(ShopModuleEntitlementClient.class);

  private final MarketplaceProperties properties;
  private final RestClient.Builder restClientBuilder;

  public ShopModuleEntitlementClient(
      MarketplaceProperties properties, RestClient.Builder restClientBuilder) {
    this.properties = properties;
    this.restClientBuilder = restClientBuilder;
  }

  public boolean isMarketplaceModuleEnabled(String shopId) {
    if (shopId == null || shopId.isBlank()) {
      return false;
    }
    try {
      RestClient client = restClientBuilder.baseUrl(properties.getShop().getBaseUrl()).build();
      String key = properties.getShop().getInternalApiKey();
      Boolean enabled =
          client
              .get()
              .uri(
                  uriBuilder ->
                      uriBuilder
                          .path("/api/v1/internal/features/shops/{shopId}/modules/enabled")
                          .queryParam("code", properties.getEntitlement().getModuleCode())
                          .build(shopId))
              .headers(
                  headers -> {
                    if (key != null && !key.isBlank()) {
                      headers.add("X-Internal-Api-Key", key);
                    }
                  })
              .retrieve()
              .body(Boolean.class);
      return Boolean.TRUE.equals(enabled);
    } catch (Exception ex) {
      log.warn("shop module probe failed shopId={}: {}", shopId, ex.toString());
      if (properties.getEntitlement().isFailOpen()) {
        return true;
      }
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "Unable to verify MARKETPLACE module entitlement");
    }
  }
}
