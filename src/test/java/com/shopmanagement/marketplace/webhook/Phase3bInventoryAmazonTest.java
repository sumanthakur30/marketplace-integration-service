package com.shopmanagement.marketplace.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.shopmanagement.marketplace.channel.AmazonChannelAdapter;
import com.shopmanagement.marketplace.channel.InventoryPushRequest;
import com.shopmanagement.marketplace.channel.ShopifyChannelAdapter;
import com.shopmanagement.marketplace.config.MarketplaceProperties;
import com.shopmanagement.marketplace.web.dto.OrderDtos;

import org.springframework.web.client.RestClient;

class Phase3bInventoryAmazonTest {

  @Test
  void amazonMapper_ingestsPilotPayload() {
    AmazonOrderWebhookMapper mapper = new AmazonOrderWebhookMapper();
    Map<String, Object> payload =
        Map.of(
            "amazonOrderId",
            "402-999",
            "orderStatus",
            "Unshipped",
            "currency",
            "INR",
            "items",
            List.of(Map.of("sellerSku", "AMZ-SKU-1", "quantityOrdered", 3, "price", "50")));
    assertThat(mapper.isOrderCreateOrUpdate("ORDER_CHANGE", payload)).isTrue();
    OrderDtos.IngestRequest req = mapper.toIngestRequest(payload);
    assertThat(req.channelCode()).isEqualTo("AMAZON");
    assertThat(req.externalOrderId()).isEqualTo("402-999");
    assertThat(req.items()).hasSize(1);
    assertThat(req.items().get(0).channelSku()).isEqualTo("AMZ-SKU-1");
  }

  @Test
  void amazonMapper_detectsCancel() {
    AmazonOrderWebhookMapper mapper = new AmazonOrderWebhookMapper();
    assertThat(
            mapper.isOrderCancelled(
                "ORDER_CHANGE", Map.of("amazonOrderId", "1", "orderStatus", "Canceled")))
        .isTrue();
  }

  @Test
  void shopifyInventoryPush_dryRunAcceptedWhenConfigured() {
    MarketplaceProperties props = new MarketplaceProperties();
    props.getChannels().getShopify().setEnabled(true);
    props.getChannels().getShopify().setDryRun(true);
    ShopifyChannelAdapter adapter = new ShopifyChannelAdapter(props, RestClient.builder());
    Map<String, Object> result =
        adapter.pushInventory(
            new InventoryPushRequest(
                "111",
                "SKU",
                7,
                Map.of(
                    "shopDomain", "demo.myshopify.com",
                    "accessToken", "shpat_x",
                    "locationId", "222"),
                Map.of("inventoryItemId", "333")));
    assertThat(result.get("accepted")).isEqualTo(true);
    assertThat(result.get("dryRun")).isEqualTo(true);
  }

  @Test
  void amazonInventoryPush_dryRunByDefault() {
    MarketplaceProperties props = new MarketplaceProperties();
    props.getChannels().getAmazon().setEnabled(true);
    props.getChannels().getAmazon().setDryRun(true);
    AmazonChannelAdapter adapter = new AmazonChannelAdapter(props, RestClient.builder());
    Map<String, Object> result =
        adapter.pushInventory(
            new InventoryPushRequest(
                "ASIN1", "SKU1", 4, Map.of("sellerId", "A1"), Map.of()));
    assertThat(result.get("accepted")).isEqualTo(true);
    assertThat(result.get("dryRun")).isEqualTo(true);
  }
}
