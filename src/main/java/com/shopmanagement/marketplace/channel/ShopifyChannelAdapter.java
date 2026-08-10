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
 * Shopify Admin inventory push. Requires channel.config_json:
 * {@code shopDomain}, {@code accessToken}, {@code locationId}; mapping.attributes {@code
 * inventoryItemId} (or listing id used as inventory_item_id).
 */
@Component
public class ShopifyChannelAdapter implements MarketplaceChannelAdapter {

  private static final Logger log = LoggerFactory.getLogger(ShopifyChannelAdapter.class);

  private final MarketplaceProperties properties;
  private final RestClient.Builder restClientBuilder;

  public ShopifyChannelAdapter(
      MarketplaceProperties properties, RestClient.Builder restClientBuilder) {
    this.properties = properties;
    this.restClientBuilder = restClientBuilder;
  }

  @Override
  public MarketplaceChannelCode code() {
    return MarketplaceChannelCode.SHOPIFY;
  }

  @Override
  public boolean isConfigured() {
    return properties.getChannels().getShopify().isEnabled();
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
    out.put("availableQty", request.availableQty());

    if (!isConfigured()) {
      out.put("accepted", false);
      out.put("reason", "CHANNEL_FLAG_OFF");
      return out;
    }

    Map<String, Object> cfg =
        request.channelConfig() == null ? Map.of() : request.channelConfig();
    Map<String, Object> attrs =
        request.mappingAttributes() == null ? Map.of() : request.mappingAttributes();

    String shopDomain = str(cfg.get("shopDomain"));
    String accessToken = str(cfg.get("accessToken"));
    String locationId = str(cfg.get("locationId"));
    String inventoryItemId =
        firstNonBlank(str(attrs.get("inventoryItemId")), request.listingId());

    if (shopDomain == null || accessToken == null || locationId == null || inventoryItemId == null) {
      out.put("accepted", false);
      out.put("reason", "MISSING_CONFIG");
      out.put(
          "hint",
          "need shopDomain+accessToken+locationId in channel config and inventoryItemId in mapping attributes");
      return out;
    }

    if (properties.getChannels().getShopify().isDryRun()) {
      out.put("accepted", true);
      out.put("dryRun", true);
      out.put("locationId", locationId);
      out.put("inventoryItemId", inventoryItemId);
      return out;
    }

    String host = shopDomain.replace("https://", "").replace("http://", "");
    if (host.endsWith("/")) {
      host = host.substring(0, host.length() - 1);
    }
    String apiVersion = properties.getChannels().getShopify().getApiVersion();
    String url =
        "https://"
            + host
            + "/admin/api/"
            + apiVersion
            + "/inventory_levels/set.json";

    Map<String, Object> body =
        Map.of(
            "location_id",
            parseLongOrString(locationId),
            "inventory_item_id",
            parseLongOrString(inventoryItemId),
            "available",
            (int) Math.floor(request.availableQty()));

    try {
      RestClient client = restClientBuilder.build();
      @SuppressWarnings("unchecked")
      Map<String, Object> response =
          client
              .post()
              .uri(url)
              .contentType(MediaType.APPLICATION_JSON)
              .header("X-Shopify-Access-Token", accessToken)
              .body(body)
              .retrieve()
              .body(Map.class);
      out.put("accepted", true);
      out.put("dryRun", false);
      out.put("response", response == null ? Map.of() : response);
      return out;
    } catch (Exception ex) {
      log.warn("Shopify inventory push failed: {}", ex.toString());
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

  private static Object parseLongOrString(String raw) {
    try {
      return Long.parseLong(raw);
    } catch (Exception ex) {
      return raw;
    }
  }

  private static String str(Object raw) {
    if (raw == null) {
      return null;
    }
    String s = String.valueOf(raw).trim();
    return s.isEmpty() ? null : s;
  }

  private static String firstNonBlank(String a, String b) {
    if (a != null && !a.isBlank()) {
      return a;
    }
    return b;
  }
}
