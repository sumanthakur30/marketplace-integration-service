package com.shopmanagement.marketplace.channel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.shopmanagement.marketplace.channel.connector.ConnectorOrderMapper;
import com.shopmanagement.marketplace.channel.connector.ConnectorUrl;
import com.shopmanagement.marketplace.channel.flipkart.FlipkartOrderMapper;
import com.shopmanagement.marketplace.settlement.SettlementAmounts;
import com.shopmanagement.marketplace.web.dto.OrderDtos;

class Phase6To9Test {

  @Test
  void mapsFlipkartShipmentWithoutBuyerFields() {
    String json =
        """
        {"shipments":[{"orderId":"OD1","buyer":{"phone":"999"},"orderItems":[{"sku":"SKU-1","fsn":"FSN1","quantity":2,"title":"Tea","priceComponents":{"sellingPrice":50}}]}],"hasMore":true}
        """;
    OrderDtos.IngestRequest request = FlipkartOrderMapper.toIngest(json).get(0);
    assertEquals("OD1", request.externalOrderId());
    assertEquals("FLIPKART", request.channelCode());
    assertEquals("SKU-1", request.items().get(0).channelSku());
    assertEquals("FSN1", request.items().get(0).channelListingId());
    assertTrue(FlipkartOrderMapper.hasMore(json));
    assertTrue(!request.toString().contains("999"));
  }

  @Test
  void refusesPrivateConnectorUrls() {
    assertThrows(IllegalArgumentException.class, () -> ConnectorUrl.requirePublicHttp("http://127.0.0.1/orders"));
    assertThrows(IllegalArgumentException.class, () -> ConnectorUrl.requirePublicHttp("http://10.1.2.3/orders"));
    assertThrows(IllegalArgumentException.class, () -> ConnectorUrl.requirePublicHttp("http://user:secret@example.com/orders"));
    assertEquals("https", ConnectorUrl.requirePublicHttp("https://partner.example.com/orders").getScheme());
  }

  @Test
  void mapsPartnerOrdersFromConfiguredFields() {
    String json =
        """
        {"rows":[{"ref":"P1","lines":[{"code":"SKU-9","qty":3,"rate":10,"listing":"L9","title":"Soap"}]}]}
        """;
    OrderDtos.IngestRequest request =
        ConnectorOrderMapper.toIngest(
                json,
                Map.of(
                    "ordersPath", "rows",
                    "orderIdField", "ref",
                    "skuField", "code",
                    "quantityField", "qty",
                    "priceField", "rate",
                    "listingField", "listing"),
                "WEBSITE")
            .get(0);
    assertEquals("P1", request.externalOrderId());
    assertEquals("WEBSITE", request.channelCode());
    assertEquals("SKU-9", request.items().get(0).channelSku());
    assertEquals(3, request.items().get(0).quantity().intValue());
  }

  @Test
  void settlementNetIsGrossMinusFeeAndCannotExceedGross() {
    SettlementAmounts.Split split = SettlementAmounts.of(new BigDecimal("100"), new BigDecimal("12.50"), null);
    assertEquals(new BigDecimal("87.50"), split.net());
    assertThrows(
        IllegalArgumentException.class,
        () -> SettlementAmounts.of(new BigDecimal("10"), new BigDecimal("11"), null));
  }
}
