package com.shopmanagement.marketplace.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.shopmanagement.marketplace.config.MarketplaceProperties;
import com.shopmanagement.marketplace.web.dto.OrderDtos;

class ShopifyWebhookPhase3Test {

  @Test
  void shopifyMapper_buildsIngestFromOrderPayload() {
    ShopifyOrderWebhookMapper mapper = new ShopifyOrderWebhookMapper();
    Map<String, Object> payload =
        Map.of(
            "id",
            555001L,
            "currency",
            "INR",
            "total_price",
            "199.00",
            "line_items",
            java.util.List.of(
                Map.of(
                    "sku",
                    "TEE-BLK-M",
                    "variant_id",
                    9001,
                    "title",
                    "Tee",
                    "quantity",
                    2,
                    "price",
                    "99.50")));
    assertThat(mapper.isOrderCreateOrUpdate("orders/create")).isTrue();
    OrderDtos.IngestRequest req = mapper.toIngestRequest(payload);
    assertThat(req.channelCode()).isEqualTo("SHOPIFY");
    assertThat(req.externalOrderId()).isEqualTo("555001");
    assertThat(req.items()).hasSize(1);
    assertThat(req.items().get(0).channelSku()).isEqualTo("TEE-BLK-M");
    assertThat(req.items().get(0).channelListingId()).isEqualTo("9001");
  }

  @Test
  void hmacVerifier_acceptsShopifyBase64WhenEnabled() {
    MarketplaceProperties props = new MarketplaceProperties();
    props.getInbound().setSigningEnabled(true);
    props.getInbound().setShopifyHmacSecret("shopify-secret");
    WebhookHmacVerifier verifier = new WebhookHmacVerifier(props);
    byte[] body = "{\"id\":1}".getBytes(StandardCharsets.UTF_8);
    byte[] mac = WebhookHmacVerifier.hmacSha256("shopify-secret", body);
    String sig = Base64.getEncoder().encodeToString(mac);
    verifier.verifyIfRequired("SHOPIFY", body, sig);
  }

  @Test
  void hmacVerifier_rejectsBadSignature() {
    MarketplaceProperties props = new MarketplaceProperties();
    props.getInbound().setSigningEnabled(true);
    props.getInbound().setHmacSecret("secret");
    WebhookHmacVerifier verifier = new WebhookHmacVerifier(props);
    assertThatThrownBy(() -> verifier.verifyIfRequired("AMAZON", "hi".getBytes(), "deadbeef"))
        .hasMessageContaining("Invalid webhook HMAC");
  }
}
