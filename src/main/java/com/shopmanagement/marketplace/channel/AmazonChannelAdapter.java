package com.shopmanagement.marketplace.channel;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.shopmanagement.marketplace.config.MarketplaceProperties;

/**
 * Amazon inventory push. Default dry-run (SP-API feeds are complex). When {@code
 * channel.config_json.inventoryEndpoint} is set and dry-run is false, POSTs a JSON quantity
 * update to that endpoint (adapter/bridge).
 */
@Component
public class AmazonChannelAdapter implements MarketplaceChannelAdapter {

  private static final Logger log = LoggerFactory.getLogger(AmazonChannelAdapter.class);

  private final MarketplaceProperties properties;
  private final RestClient.Builder restClientBuilder;

  public AmazonChannelAdapter(
      MarketplaceProperties properties, RestClient.Builder restClientBuilder) {
    this.properties = properties;
    this.restClientBuilder = restClientBuilder;
  }

  @Override
  public MarketplaceChannelCode code() {
    return MarketplaceChannelCode.AMAZON;
  }

  @Override
  public boolean isConfigured() {
    return properties.getChannels().getAmazon().isEnabled();
  }

  @Override
  public Map<String, Object> pushInventory(String listingId, String channelSku, double availableQty) {
    return pushInventory(new InventoryPushRequest(listingId, channelSku, availableQty, Map.of(), Map.of()));
  }

  @Override
  public Map<String, Object> pushInventory(InventoryPushRequest request) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("channel", code().name());
    out.put("listingId", request.listingId() == null ? "" : request.listingId());
    out.put("channelSku", request.channelSku() == null ? "" : request.channelSku());
    out.put("availableQty", request.availableQty());

    if (!isConfigured()) {
      out.put("accepted", false);
      out.put("reason", "CHANNEL_FLAG_OFF");
      return out;
    }

    Map<String, Object> cfg =
        request.channelConfig() == null ? Map.of() : request.channelConfig();
    String endpoint = str(cfg.get("inventoryEndpoint"));
    if (endpoint == null) {
      endpoint = properties.getChannels().getAmazon().getInventoryEndpoint();
    }

    boolean dryRun =
        properties.getChannels().getAmazon().isDryRun()
            || endpoint == null
            || endpoint.isBlank();

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("sellerSku", request.channelSku());
    body.put("listingId", request.listingId());
    body.put("quantity", (int) Math.floor(request.availableQty()));
    body.put("sellerId", cfg.get("sellerId"));
    body.put("marketplaceId", cfg.get("marketplaceId"));

    if (dryRun) {
      out.put("accepted", true);
      out.put("dryRun", true);
      out.put("payload", body);
      out.put(
          "hint",
          "Set marketplace.channels.amazon.dry-run=false and inventoryEndpoint (or channel config) for live push");
      return out;
    }

    try {
      RestClient client = restClientBuilder.build();
      @SuppressWarnings("unchecked")
      Map<String, Object> response =
          client
              .post()
              .uri(endpoint)
              .contentType(MediaType.APPLICATION_JSON)
              .headers(
                  h -> {
                    Object token = cfg.get("accessToken");
                    if (token != null && !String.valueOf(token).isBlank()) {
                      h.add("Authorization", "Bearer " + token);
                    }
                  })
              .body(body)
              .retrieve()
              .body(Map.class);
      out.put("accepted", true);
      out.put("dryRun", false);
      out.put("response", response == null ? Map.of() : response);
      return out;
    } catch (Exception ex) {
      log.warn("Amazon inventory push failed: {}", ex.toString());
      out.put("accepted", false);
      out.put("reason", "HTTP_ERROR");
      out.put("error", ex.getMessage());
      return out;
    }
  }

  @Override
  public Map<String, Object> pullOrders(int limit) {
    return Map.of("channel", code().name(), "orders", List.of(), "stub", true);
  }

  private static String str(Object raw) {
    if (raw == null) {
      return null;
    }
    String s = String.valueOf(raw).trim();
    return s.isEmpty() ? null : s;
  }
}
